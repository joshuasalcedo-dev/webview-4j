//! Files dragged out of the app, to Explorer, the desktop, a mail, another app (Windows): the Shell makes the files'
//! data object, so a drop works as one from Explorer does; the drag follows the person's mouse until they let go.

/// What a drop did: `copy`, `move`, `link`, or `none` (let go where nothing takes files, or Escape).
#[cfg(windows)]
pub fn drag_files(paths: &[std::path::PathBuf]) -> Result<&'static str, String> {
    use windows::Win32::Foundation::{DRAGDROP_S_CANCEL, DRAGDROP_S_DROP, DRAGDROP_S_USEDEFAULTCURSORS, S_OK};
    use windows::Win32::System::Com::IDataObject;
    use windows::Win32::System::Ole::{DROPEFFECT, DROPEFFECT_COPY, DROPEFFECT_LINK, DROPEFFECT_MOVE, DoDragDrop, IDropSource, IDropSource_Impl};
    use windows::Win32::System::SystemServices::{MK_LBUTTON, MK_RBUTTON, MODIFIERKEYS_FLAGS};
    use windows::Win32::UI::Shell::Common::ITEMIDLIST;
    use windows::Win32::UI::Shell::{BHID_DataObject, ILFree, SHCreateShellItemArrayFromIDLists, SHParseDisplayName};
    use windows::core::{BOOL, HRESULT, HSTRING, implement};

    /// The drag goes on while a button is held; Escape cancels it; letting go drops.
    #[implement(IDropSource)]
    struct Source;

    impl IDropSource_Impl for Source_Impl {
        fn QueryContinueDrag(&self, escape: BOOL, keys: MODIFIERKEYS_FLAGS) -> HRESULT {
            if escape.as_bool() {
                DRAGDROP_S_CANCEL
            } else if keys.0 & (MK_LBUTTON.0 | MK_RBUTTON.0) == 0 {
                DRAGDROP_S_DROP
            } else {
                S_OK
            }
        }

        fn GiveFeedback(&self, _effect: DROPEFFECT) -> HRESULT {
            DRAGDROP_S_USEDEFAULTCURSORS
        }
    }

    if paths.is_empty() {
        return Err("no files to drag".into());
    }
    let mut ids: Vec<*mut ITEMIDLIST> = Vec::new();
    let free = |ids: &[*mut ITEMIDLIST]| {
        for id in ids {
            unsafe { ILFree(Some(*id as *const _)) };
        }
    };
    for path in paths {
        let mut id: *mut ITEMIDLIST = std::ptr::null_mut();
        // SAFETY: a path the Shell parses into an item id it allocates; freed below.
        if let Err(e) = unsafe { SHParseDisplayName(&HSTRING::from(path.as_os_str()), None, &mut id, 0, None) } {
            free(&ids);
            return Err(format!("{}: {}", path.display(), e.message()));
        }
        ids.push(id);
    }
    let constant: Vec<*const ITEMIDLIST> = ids.iter().map(|id| *id as *const ITEMIDLIST).collect();
    let data: Result<IDataObject, String> = unsafe { SHCreateShellItemArrayFromIDLists(&constant) }
        .and_then(|items| unsafe { items.BindToHandler(None, &BHID_DataObject) })
        .map_err(|e| e.message().to_string());
    free(&ids);
    let data = data?;
    let source: IDropSource = Source.into();
    let mut effect = DROPEFFECT(0);
    // SAFETY: on the event loop's thread (OLE is started there): the drag runs its own loop until the drop.
    let done = unsafe { DoDragDrop(&data, &source, DROPEFFECT_COPY | DROPEFFECT_LINK | DROPEFFECT_MOVE, &mut effect) };
    if done != DRAGDROP_S_DROP && done != DRAGDROP_S_CANCEL {
        return Err(format!("the drag failed ({done:?})"));
    }
    Ok(if done == DRAGDROP_S_CANCEL || effect.0 == 0 {
        "none"
    } else if effect.0 & DROPEFFECT_MOVE.0 != 0 {
        "move"
    } else if effect.0 & DROPEFFECT_COPY.0 != 0 {
        "copy"
    } else {
        "link"
    })
}

#[cfg(not(windows))]
pub fn drag_files(_paths: &[std::path::PathBuf]) -> Result<&'static str, String> {
    Err("dragging files out is done on Windows for now".into())
}
