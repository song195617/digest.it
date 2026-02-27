from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql+psycopg2://digest:digest@localhost:5432/digestit"
    redis_url: str = "redis://localhost:6379/0"
    openai_api_key: str = ""
    claude_api_key: str = ""
    audio_tmp_dir: str = "/tmp/digestit_audio"
    whisper_model: str = "large-v3"
    whisper_device: str = "cpu"
    whisper_compute_type: str = "int8"
    debug: bool = False

    class Config:
        env_file = ".env"


settings = Settings()
