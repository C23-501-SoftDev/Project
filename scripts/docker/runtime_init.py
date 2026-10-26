"""Prepare only Compose-owned secrets/content volumes; never reset database data."""
import fcntl
import os
from pathlib import Path
import re
import secrets
import stat
import tempfile


class InitializationError(Exception):
    pass


SECRET_LENGTHS = {"db_password": 64, "jwt_secret": 96}


def directory(path, uid, gid, mode):
    if path.is_symlink():
        raise InitializationError("A runtime directory is a symlink; refusing to follow it.")
    path.mkdir(parents=True, exist_ok=True, mode=mode)
    if not path.is_dir():
        raise InitializationError("A runtime path is not a directory.")
    os.chown(path, uid, gid)
    path.chmod(mode)


def verify_secret_set(current, uid, gid):
    if current.is_symlink() or not current.is_dir():
        raise InitializationError("The published secret set is not an ordinary directory.")
    info = current.stat()
    if info.st_uid != uid or info.st_gid != gid or stat.S_IMODE(info.st_mode) != 0o700:
        raise InitializationError("The published secret directory must be app-owned with mode 0700.")
    if {item.name for item in current.iterdir()} != set(SECRET_LENGTHS):
        raise InitializationError("The secret set is incomplete or unexpected; restore its matching backup, do not regenerate it.")
    for name, length in SECRET_LENGTHS.items():
        path = current / name
        if path.is_symlink() or not path.is_file():
            raise InitializationError("A secret file is not an ordinary file.")
        info = path.stat()
        if info.st_uid != uid or info.st_gid != gid or stat.S_IMODE(info.st_mode) != 0o400:
            raise InitializationError("Secret ownership or permissions changed; expected app-owned mode 0400.")
        if not re.fullmatch(r"[0-9a-f]{" + str(length) + r"}\n", path.read_text()):
            raise InitializationError("A secret file is malformed; values are intentionally not printed.")


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def initialize(secret_root=Path("/run/kb-secrets"), content_root=Path("/data"),
               database_root=Path("/database"), uid=1000, gid=1000):
    os.umask(0o077)
    directory(secret_root, os.getuid(), gid, 0o750)
    lock_path = secret_root / ".init.lock"
    if lock_path.is_symlink():
        raise InitializationError("The initialization lock is a symlink.")
    with open(lock_path, "a") as lock:
        os.chmod(lock_path, 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX)
        unexpected = {path.name for path in secret_root.iterdir()} - {".init.lock", "current"}
        if unexpected:
            raise InitializationError("Unpublished or unexpected secret files exist; inspect the volume before retrying.")
        current = secret_root / "current"
        if current.exists() or current.is_symlink():
            verify_secret_set(current, uid, gid)
            created = False
        else:
            if database_root.is_symlink() or not database_root.is_dir():
                raise InitializationError("The database volume must be mounted as an ordinary directory.")
            # Includes official PG18 /18/docker/PG_VERSION, older layouts and partial initdb state.
            if any(database_root.iterdir()):
                raise InitializationError("Database state exists without its secrets; restore the matching secrets volume. Nothing was overwritten.")
            if content_root.exists() and any(content_root.iterdir()):
                raise InitializationError("Content state exists without its secrets; inspect the matching volume set before creating new secrets.")
            temporary = Path(tempfile.mkdtemp(prefix=".unpublished-", dir=secret_root))
            # Publish the pair with one directory rename. A failed preparation remains visible and is never silently rotated.
            for name, length in SECRET_LENGTHS.items():
                path = temporary / name
                with path.open("x") as output:
                    output.write(secrets.token_hex(length // 2) + "\n")
                    output.flush()
                    os.fsync(output.fileno())
                os.chown(path, uid, gid)
                path.chmod(0o400)
            directory(temporary, uid, gid, 0o700)
            fsync_directory(temporary)
            os.rename(temporary, current)
            fsync_directory(secret_root)
            verify_secret_set(current, uid, gid)
            created = True
        # Only these volume directories get ownership updates; never traverse/chown user files or PostgreSQL.
        directory(content_root, uid, gid, 0o700)
        directory(content_root / "git", uid, gid, 0o700)
        directory(content_root / "blobs", uid, gid, 0o700)
    print("Runtime volumes ready; " + ("new secret set created." if created else "existing secrets and content preserved."), flush=True)
    return created


if __name__ == "__main__":
    try:
        initialize()
    except (InitializationError, OSError) as error:
        # Secret values and environments never appear in diagnostics.
        message = str(error) if isinstance(error, InitializationError) else "Filesystem preparation failed; inspect volume ownership and mounts."
        raise SystemExit("runtime-init: " + message)
