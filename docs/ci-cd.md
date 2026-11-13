# CI/CD: переключение на GitLab CI

**Документ-заглушка.** Полная процедура появится вместе с задачей
`### Hot-swap CI на GitLab` (`docs/ci-roadmap.md`).

## Назначение

Единая точка входа для процедуры бесшовного переключения CI/CD между
GitHub Actions (основной) и GitLab CI (резерв). Когда задача Hot-swap
будет реализована — сюда переедут: активация pipeline в GitLab,
отключение GitHub Actions, синхронизация секретов, запуск конкретного
job'а.

## Ссылки

- `docs/ci-roadmap.md` → `### Hot-swap CI на GitLab` — план.
- `docs/ci-roadmap.md` → `### Self-hosted GitLab Runner через Ansible + Podman` — предусловие.
- `docs/repo-protection.md` → «GitLab Protected Branches / Tags».
- `docs/git-flow.md` → схема веток и стратегия merge (общая для обеих CI).
