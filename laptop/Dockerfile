# CaptureOS backend for the cloud (Railway). Data lives on a volume mounted at /data.
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENV DATA_DIR=/data \
    OFFICEKIT_WATCH=0 \
    PYTHONUNBUFFERED=1

# Railway sets $PORT; default 8000 elsewhere.
CMD ["sh", "-c", "uvicorn server:app --host 0.0.0.0 --port ${PORT:-8000}"]
