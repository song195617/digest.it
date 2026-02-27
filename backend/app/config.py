from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql+psycopg2://digest:digest@localhost:5432/digestit"
    redis_url: str = "redis://localhost:6379/0"
    openai_api_key: str = ""
    claude_api_key: str = ""
    audio_tmp_dir: str = "/tmp/digestit_audio"
    max_audio_chunk_minutes: int = 10  # Split audio into 10-min chunks for Whisper
    debug: bool = False

    class Config:
        env_file = ".env"


settings = Settings()
