from pydantic_settings import BaseSettings, SettingsConfigDict


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
    cors_allowed_origins_raw: str = (
        "http://localhost,"
        "http://127.0.0.1,"
        "http://localhost:3000,"
        "http://127.0.0.1:3000,"
        "http://10.0.2.2,"
        "http://10.0.2.2:8000"
    )

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @property
    def cors_allowed_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_allowed_origins_raw.split(",") if origin.strip()]


settings = Settings()
