"""App database. SQLite file by default; set DATABASE_URL (e.g. a Postgres URL) to move to the cloud."""
import os
import secrets
import string
from datetime import datetime, timezone

from sqlmodel import Field, Session, SQLModel, create_engine

from config import DATA

DATABASE_URL = os.environ.get("DATABASE_URL", f"sqlite:///{(DATA / 'captureos.db').as_posix()}")
engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {},
)


def now() -> datetime:
    return datetime.now(timezone.utc)


def new_token() -> str:
    return secrets.token_urlsafe(24)


def new_code() -> str:
    return "".join(secrets.choice(string.ascii_uppercase + string.digits) for _ in range(6))


class Team(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    name: str
    code: str = Field(index=True, unique=True)
    created_at: datetime = Field(default_factory=now)


class User(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    team_id: int = Field(foreign_key="team.id", index=True)
    name: str
    aliases: str = ""  # comma-separated nicknames, e.g. "Teja,Tej"
    role: str = "member"  # admin | member
    profile: str = "employee"  # employee | student | manager | field -- what the inbox treats as important
    token: str | None = Field(default=None, index=True, unique=True)  # set when the person joins on their phone

    def names(self) -> list[str]:
        return [self.name, *[a.strip() for a in self.aliases.split(",") if a.strip()]]


class Meeting(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    team_id: int = Field(foreign_key="team.id", index=True)
    host_id: int = Field(foreign_key="user.id")
    title: str = ""
    status: str = "uploaded"  # uploaded | transcribing | thinking | done | error
    error: str = ""
    audio_path: str = ""
    language: str = ""
    transcript: str = ""
    summary: str = ""
    notion_url: str = ""
    created_at: datetime = Field(default_factory=now)


class Attendee(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    meeting_id: int = Field(foreign_key="meeting.id", index=True)
    user_id: int | None = Field(default=None, foreign_key="user.id")
    name: str
    speaker_label: str = ""
    update: str = ""  # "done ...; next ...; blocked ..."


class Segment(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    meeting_id: int = Field(foreign_key="meeting.id", index=True)
    idx: int
    speaker: str  # label as it appears in the transcript ("Speaker 2" or a name)
    name: str = ""  # resolved person name, if known
    user_id: int | None = Field(default=None, foreign_key="user.id")
    text: str


class Task(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    team_id: int = Field(foreign_key="team.id", index=True)
    meeting_id: int | None = Field(default=None, foreign_key="meeting.id")
    owner_name: str
    owner_user_id: int | None = Field(default=None, foreign_key="user.id", index=True)
    title: str
    due: str | None = None
    priority: str = "medium"
    status: str = "todo"  # todo | doing | done
    notion_url: str = ""
    created_at: datetime = Field(default_factory=now)


class Question(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    meeting_id: int = Field(foreign_key="meeting.id", index=True)
    text: str
    answer: str = ""
    status: str = "open"  # open | answered


class FieldVisit(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    team_id: int = Field(foreign_key="team.id", index=True)
    created_by: int = Field(foreign_key="user.id")
    site: str
    report_status: str = "none"  # none | building | done | error
    report_error: str = ""
    summary: str = ""
    key_issues: str = ""  # newline-separated
    recommendations: str = ""  # newline-separated
    report_path: str = ""
    notion_url: str = ""
    created_at: datetime = Field(default_factory=now)


class Observation(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    visit_id: int = Field(foreign_key="fieldvisit.id", index=True)
    status: str = "processing"  # processing | done | error
    error: str = ""
    photo_path: str
    voice_path: str = ""
    voice_text: str = ""
    note: str = ""
    title: str = ""
    observation: str = ""
    category: str = ""
    severity: str = ""  # none | low | medium | high
    action: str = ""
    owner: str = ""
    task_id: int | None = Field(default=None, foreign_key="task.id")
    created_at: datetime = Field(default_factory=now)


class InboxItem(SQLModel, table=True):
    """One photo or audio file from the phone (app upload or Office Kit transfer)."""

    id: int | None = Field(default=None, primary_key=True)
    team_id: int = Field(foreign_key="team.id", index=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    kind: str = "photo"  # photo | audio
    source: str = "app"  # app | officekit
    client_id: str = Field(default="", index=True)  # gallery id / file hash, so re-sends are skipped
    content_hash: str = Field(default="", index=True)  # same file from the app and Office Kit counts once
    taken_at: str = ""
    path: str = ""
    status: str = "queued"  # queued | done | error
    album: str = ""  # AI-named smart album, e.g. "Lecture slides - DBMS"
    category: str = ""
    people: bool = False
    actionable: bool = False  # matches the user's criteria (work/study) -> text + to-dos extracted
    title: str = ""
    text: str = ""  # text read from the photo, or the audio transcript
    summary: str = ""
    task_ids: str = ""  # comma-separated
    meeting_id: int | None = Field(default=None, foreign_key="meeting.id")  # audio that was a meeting
    error: str = ""
    created_at: datetime = Field(default_factory=now)


def init() -> None:
    SQLModel.metadata.create_all(engine)


def session() -> Session:
    return Session(engine)
