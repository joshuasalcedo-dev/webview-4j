//! Native dialogs (`app.dialog.file`, `app.dialog.message`): open, save, pick a folder, and message
//! boxes. Each is built on the event loop thread, where its parent window lives, and returned as a
//! future the runtime waits for on a thread of its own: a dialog never holds the loop up, so the
//! app's other windows keep working while it is open.

use std::future::Future;
use std::pin::Pin;

use serde_json::json;
use tao::window::Window;

use crate::payload::{FileDialogBody, FileDialogKind, MessageButtons, MessageDialogBody, MessageLevel, Outcome};

pub type Answer = Pin<Box<dyn Future<Output = Outcome> + Send>>;

/// An open, save or folder dialog. Answers `{"paths":[...]}`: empty when the user cancelled.
pub fn file(body: FileDialogBody, parent: Option<&Window>) -> Answer {
    let mut dialog = rfd::AsyncFileDialog::new();
    if let Some(title) = &body.title {
        dialog = dialog.set_title(title);
    }
    if let Some(directory) = &body.directory {
        dialog = dialog.set_directory(directory);
    }
    if let Some(name) = &body.file_name {
        dialog = dialog.set_file_name(name);
    }
    for filter in &body.filters {
        let extensions: Vec<&str> = filter.extensions.iter().map(|e| e.trim_start_matches('.')).collect();
        dialog = dialog.add_filter(&filter.name, &extensions);
    }
    if let Some(window) = parent {
        dialog = dialog.set_parent(window);
    }
    match body.kind {
        FileDialogKind::Open => Box::pin(async move { paths(dialog.pick_file().await.into_iter().collect()) }),
        FileDialogKind::OpenMany => Box::pin(async move { paths(dialog.pick_files().await.unwrap_or_default()) }),
        FileDialogKind::Save => Box::pin(async move { paths(dialog.save_file().await.into_iter().collect()) }),
        FileDialogKind::Folder => Box::pin(async move { paths(dialog.pick_folder().await.into_iter().collect()) }),
    }
}

fn paths(handles: Vec<rfd::FileHandle>) -> Outcome {
    let paths: Vec<String> = handles.iter().map(|h| h.path().to_string_lossy().into_owned()).collect();
    Outcome::json(&json!({ "paths": paths }))
}

/// A message box. Answers `{"answer":"ok"|"cancel"|"yes"|"no"}`; closing it without a button
/// answers `cancel` (or `no` for yes/no).
pub fn message(body: MessageDialogBody, parent: Option<&Window>) -> Answer {
    let mut dialog = rfd::AsyncMessageDialog::new()
        .set_title(&body.title)
        .set_description(&body.text)
        .set_level(match body.level {
            MessageLevel::Info => rfd::MessageLevel::Info,
            MessageLevel::Warning => rfd::MessageLevel::Warning,
            MessageLevel::Error => rfd::MessageLevel::Error,
        })
        .set_buttons(match body.buttons {
            MessageButtons::Ok => rfd::MessageButtons::Ok,
            MessageButtons::OkCancel => rfd::MessageButtons::OkCancel,
            MessageButtons::YesNo => rfd::MessageButtons::YesNo,
            MessageButtons::YesNoCancel => rfd::MessageButtons::YesNoCancel,
        });
    if let Some(window) = parent {
        dialog = dialog.set_parent(window);
    }
    let buttons = body.buttons;
    Box::pin(async move {
        let answer = match dialog.show().await {
            rfd::MessageDialogResult::Ok => "ok",
            rfd::MessageDialogResult::Yes => "yes",
            rfd::MessageDialogResult::No => "no",
            rfd::MessageDialogResult::Cancel => "cancel",
            // A backend that names its buttons: by their label, else as dismissed.
            rfd::MessageDialogResult::Custom(label) => match label.to_ascii_lowercase().as_str() {
                "ok" => "ok",
                "yes" => "yes",
                "no" => "no",
                _ if buttons == MessageButtons::YesNo => "no",
                _ => "cancel",
            },
        };
        Outcome::json(&json!({ "answer": answer }))
    })
}
