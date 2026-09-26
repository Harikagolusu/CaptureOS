"""App API: teams, login by team code, meetings, and each member's own view.

Auth: every call except /team (create) and /join sends `Authorization: Bearer <token>`.
"""
from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, Header, HTTPException, UploadFile
from pydantic import BaseModel
from sqlmodel import select

import notion
from config import DATA, DRY_RUN
from db import Attendee, Meeting, Question, Segment, Task, Team, User, new_code, new_token, session
from pipeline import process_meeting

router = APIRouter()
AUDIO_DIR = DATA / "meetings"


# ---------- auth ----------

def current_user(authorization: str = Header("")) -> User:
    token = authorization.removeprefix("Bearer ").strip()
    with session() as s:
        user = s.exec(select(User).where(User.token == token)).first() if token else None
    if not user:
        raise HTTPException(401, "Missing or invalid token")
    return user


def admin_user(user: User = Depends(current_user)) -> User:
    if user.role != "admin":
        raise HTTPException(403, "Admins only")
    return user


def _profile(p: str) -> str:
    if p not in PROFILES:
        raise HTTPException(422, f"profile must be one of {', '.join(PROFILES)}")
    return p


def _user_out(u: User, with_token: bool = False) -> dict:
    out = {"id": u.id, "name": u.name, "aliases": u.names()[1:], "role": u.role, "profile": u.profile, "joined": bool(u.token)}
    if with_token:
        out["token"] = u.token
    return out


# ---------- team & login ----------

PROFILES = ("employee", "student", "manager", "field")


class CreateTeam(BaseModel):
    team_name: str
    admin_name: str
    aliases: list[str] = []
    profile: str = "manager"


class Join(BaseModel):
    code: str
    name: str
    profile: str = "employee"


class AddMember(BaseModel):
    name: str
    aliases: list[str] = []


@router.post("/team")
def create_team(body: CreateTeam):
    """Admin's first screen: creates the team, returns the join code and the admin's token."""
    with session() as s:
        team = Team(name=body.team_name, code=new_code())
        s.add(team)
        s.flush()
        admin = User(team_id=team.id, name=body.admin_name, aliases=",".join(body.aliases), role="admin", token=new_token(), profile=_profile(body.profile))
        s.add(admin)
        s.commit()
        return {"team": {"id": team.id, "name": team.name, "code": team.code}, "user": _user_out(admin, with_token=True)}


@router.post("/join")
def join(body: Join):
    """Member's first screen: team code + their name. Picks the member the admin added, or adds them."""
    with session() as s:
        team = s.exec(select(Team).where(Team.code == body.code.strip().upper())).first()
        if not team:
            raise HTTPException(404, "Wrong team code")
        members = list(s.exec(select(User).where(User.team_id == team.id)))
        wanted = body.name.strip().lower()
        user = next((u for u in members if wanted in (n.lower() for n in u.names())), None)
        if user and user.role == "admin":
            raise HTTPException(403, "That name belongs to the admin")
        if not user:
            user = User(team_id=team.id, name=body.name.strip())
        user.token = new_token()  # joining again (new phone) issues a fresh token
        user.profile = _profile(body.profile)
        s.add(user)
        s.commit()
        s.refresh(user)
        return {"team": {"id": team.id, "name": team.name}, "user": _user_out(user, with_token=True)}


@router.get("/me")
def me(user: User = Depends(current_user)):
    return _user_out(user)


class MePatch(BaseModel):
    profile: str


@router.patch("/me")
def update_me(body: MePatch, user: User = Depends(current_user)):
    """Change what the smart inbox treats as important: employee | student | manager | field."""
    with session() as s:
        u = s.get(User, user.id)
        u.profile = _profile(body.profile)
        s.add(u)
        s.commit()
        s.refresh(u)
        return _user_out(u)


@router.get("/team")
def team(user: User = Depends(current_user)):
    with session() as s:
        t = s.get(Team, user.team_id)
        members = s.exec(select(User).where(User.team_id == t.id)).all()
        out = {"id": t.id, "name": t.name, "members": [_user_out(u) for u in members]}
        if user.role == "admin":
            out["code"] = t.code
        return out


@router.post("/team/members")
def add_member(body: AddMember, admin: User = Depends(admin_user)):
    with session() as s:
        u = User(team_id=admin.team_id, name=body.name.strip(), aliases=",".join(a.strip() for a in body.aliases))
        s.add(u)
        s.commit()
        s.refresh(u)
        return _user_out(u)


# ---------- meetings ----------

@router.post("/meetings")
async def upload_meeting(
    background: BackgroundTasks,
    audio: UploadFile = File(...),
    title: str = Form(""),
    language: str = Form(""),
    user: User = Depends(current_user),
):
    """Upload a recorded meeting (audio or video). Returns at once; poll GET /meetings/{id} for status."""
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    with session() as s:
        m = Meeting(team_id=user.team_id, host_id=user.id, title=title, language=language)
        s.add(m)
        s.commit()
        s.refresh(m)
        path = AUDIO_DIR / f"{m.id}{Path(audio.filename or '').suffix or '.m4a'}"
        path.write_bytes(await audio.read())
        m.audio_path = str(path)
        s.add(m)
        s.commit()
        meeting_id = m.id
    background.add_task(process_meeting, meeting_id)
    return {"id": meeting_id, "status": "uploaded"}


def _meeting(s, meeting_id: int, user: User) -> Meeting:
    m = s.get(Meeting, meeting_id)
    if not m or m.team_id != user.team_id:
        raise HTTPException(404, "Meeting not found")
    return m


def _task_out(t: Task) -> dict:
    return {
        "id": t.id, "title": t.title, "owner": t.owner_name, "owner_user_id": t.owner_user_id, "due": t.due,
        "priority": t.priority, "status": t.status, "meeting_id": t.meeting_id, "notion_url": t.notion_url,
    }


@router.get("/meetings")
def list_meetings(user: User = Depends(current_user)):
    with session() as s:
        rows = s.exec(select(Meeting).where(Meeting.team_id == user.team_id).order_by(Meeting.created_at.desc())).all()
        return [{"id": m.id, "title": m.title, "status": m.status, "created_at": m.created_at, "summary": m.summary} for m in rows]


@router.get("/meetings/{meeting_id}")
def get_meeting(meeting_id: int, user: User = Depends(current_user)):
    """Full meeting (everyone in the team can see it): status, summary, attendees, tasks, questions, who said what."""
    with session() as s:
        m = _meeting(s, meeting_id, user)
        return {
            "id": m.id, "title": m.title, "status": m.status, "error": m.error, "created_at": m.created_at,
            "summary": m.summary, "notion_url": m.notion_url,
            "attendees": [a.model_dump(exclude={"meeting_id"}) for a in s.exec(select(Attendee).where(Attendee.meeting_id == m.id))],
            "tasks": [_task_out(t) for t in s.exec(select(Task).where(Task.meeting_id == m.id))],
            "questions": [q.model_dump(exclude={"meeting_id"}) for q in s.exec(select(Question).where(Question.meeting_id == m.id))],
            "segments": [
                {"speaker": g.speaker, "name": g.name, "user_id": g.user_id, "text": g.text}
                for g in s.exec(select(Segment).where(Segment.meeting_id == m.id).order_by(Segment.idx))
            ],
        }


# ---------- my view ----------

@router.get("/me/tasks")
def my_tasks(status: str = "", user: User = Depends(current_user)):
    with session() as s:
        q = select(Task).where(Task.owner_user_id == user.id)
        if status:
            q = q.where(Task.status == status)
        return [_task_out(t) for t in s.exec(q.order_by(Task.created_at.desc()))]


@router.get("/me/meetings/{meeting_id}")
def my_meeting(meeting_id: int, user: User = Depends(current_user)):
    """One member's slice of a meeting: their update, their tasks, what they said, plus the summary."""
    with session() as s:
        m = _meeting(s, meeting_id, user)
        mine = s.exec(select(Attendee).where(Attendee.meeting_id == m.id, Attendee.user_id == user.id)).first()
        return {
            "id": m.id, "title": m.title, "status": m.status, "summary": m.summary,
            "my_update": mine.update if mine else "",
            "my_tasks": [_task_out(t) for t in s.exec(select(Task).where(Task.meeting_id == m.id, Task.owner_user_id == user.id))],
            "i_said": [g.text for g in s.exec(select(Segment).where(Segment.meeting_id == m.id, Segment.user_id == user.id).order_by(Segment.idx))],
        }


class TaskPatch(BaseModel):
    status: str  # todo | doing | done


@router.patch("/tasks/{task_id}")
def update_task(task_id: int, body: TaskPatch, user: User = Depends(current_user)):
    if body.status not in ("todo", "doing", "done"):
        raise HTTPException(422, "status must be todo, doing or done")
    with session() as s:
        t = s.get(Task, task_id)
        if not t or t.team_id != user.team_id:
            raise HTTPException(404, "Task not found")
        if user.role != "admin" and t.owner_user_id != user.id:
            raise HTTPException(403, "Not your task")
        t.status = body.status
        s.add(t)
        s.commit()
        s.refresh(t)
        if t.notion_url and not DRY_RUN:
            try:
                notion.set_status(t.notion_url, t.status)
            except Exception:
                pass  # the app's DB is the source of truth; Notion is a mirror
        return _task_out(t)


class Answer(BaseModel):
    answer: str


@router.post("/questions/{question_id}/answer")
def answer_question(question_id: int, body: Answer, admin: User = Depends(admin_user)):
    with session() as s:
        q = s.get(Question, question_id)
        m = q and s.get(Meeting, q.meeting_id)
        if not m or m.team_id != admin.team_id:
            raise HTTPException(404, "Question not found")
        q.answer, q.status = body.answer, "answered"
        s.add(q)
        s.commit()
        return q.model_dump()
