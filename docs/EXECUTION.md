# Execution plan: meeting brain v1

**Roles:** Claude = senior (decides and acts through tools). Gemma = junior (detailed transcript write-up). They run in parallel on the same transcript.

```
app records meeting → POST /meeting (audio)
  → GPT-4o transcribe (AI4Bharat later for Telugu)
  → transcript ─┬─→ Claude agent → create_task / create_note / ask_user → Notion
                └─→ Gemma (Ollama) → detailed notes markdown
  → app shows actions + notes
```

## Owners
| Who | Branch | Delivers |
|---|---|---|
| Tej (senior brain) | `laptop/brain` | `/transcribe`, `/process`, `/meeting`, Claude agent, Notion writes |
| Teammate A | `phone/*` | Flutter: record → upload `/meeting` → show actions + questions |
| Teammate B | `laptop/gemma` | `gemma_notes(transcript) -> markdown` via Ollama; add to `/meeting` in parallel |

**Contract everyone codes against:** `laptop/README.md` → Action shape. Don't change it without telling the team.

## Steps
1. ✅ Server skeleton, Claude agent (dry-run), GPT-4o transcription code
2. ⬜ Keys in `laptop/.env` → run `samples/meeting_mixed.txt` through `/process`, check the actions make sense
3. ✅ Notion: tasks DB + meeting notes page live (setup_notion.py); real writes verified
4. ⬜ Record a real 1-min Telugu/English clip on the phone → `/meeting` end to end
5. ⬜ Gemma notes merged in (teammate B), run with `asyncio.gather` next to Claude
6. ⬜ Flutter hits `/meeting` over the hotspot (teammate A)
   - **Save to Notes button** (teammate A): after `/meeting` returns, share summary + tasks + decisions as text via `share_plus` → user picks vivo Notes → syncs to PC through Office Kit. No token needed.
7. ⬜ AI4Bharat IndicConformer spike: compare against GPT-4o on the same Telugu clip, keep the better one
8. ⬜ Measure latency per stage; put the real numbers in the pitch

## Rules
- Feature branches + PR to `main`, 1 review.
- Never commit `.env`, audio, or `laptop/data/`.
