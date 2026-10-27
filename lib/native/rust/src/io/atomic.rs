//! Publishes the compiled config through a temp file + rename, so the core never observes a
//! half-written runtime config.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};

static NEXT_TEMP_ID: AtomicU64 = AtomicU64::new(0);

pub fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let parent = path
        .parent()
        .ok_or_else(|| "runtime output path has no parent".to_string())?;
    fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| "runtime output path has no valid file name".to_string())?;
    let temp_id = NEXT_TEMP_ID.fetch_add(1, Ordering::Relaxed);
    let tmp = parent.join(format!(
        ".{file_name}.{}.{}.tmp",
        std::process::id(),
        temp_id
    ));

    let publish = (|| -> Result<(), String> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&tmp)
            .map_err(|err| err.to_string())?;
        file.write_all(bytes).map_err(|err| err.to_string())?;
        file.sync_all().map_err(|err| err.to_string())?;
        drop(file);
        fs::rename(&tmp, path).map_err(|err| err.to_string())?;
        Ok(())
    })();
    if publish.is_err() {
        let _ = fs::remove_file(&tmp);
    }
    publish
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn write_atomic_creates_parents_and_leaves_no_temp_file() {
        let dir = std::env::temp_dir().join(format!(
            "yumebox-write-atomic-{}-{}",
            std::process::id(),
            NEXT_TEMP_ID.fetch_add(1, Ordering::Relaxed)
        ));
        let target = dir.join("nested").join("runtime.yaml");
        write_atomic(&target, b"mode: rule\n").expect("write runtime yaml");
        assert_eq!(
            fs::read_to_string(&target).expect("read back"),
            "mode: rule\n"
        );

        write_atomic(&target, b"mode: global\n").expect("overwrite runtime yaml");
        assert_eq!(
            fs::read_to_string(&target).expect("read back"),
            "mode: global\n"
        );

        let leftovers = fs::read_dir(target.parent().expect("parent"))
            .expect("list dir")
            .filter_map(Result::ok)
            .filter(|entry| entry.file_name().to_string_lossy().ends_with(".tmp"))
            .count();
        assert_eq!(leftovers, 0, "temp files must not survive a publish");

        let _ = fs::remove_dir_all(&dir);
    }
}
