//! The app's child processes, tied to its life (Windows): each is put in one Job Object that ends its processes when
//! its last handle closes, the app's. So a child, and what it starts, ends with the app, however the app ends: quit,
//! a crash, killed from the Task Manager. Elsewhere the app ends its children when it closes (Java does).

/// Puts process `pid` in the app's job: 1 done, 0 not (it ended already, or the system refused), -1 not on this
/// system.
#[cfg(windows)]
pub fn assign(pid: u32) -> i32 {
    use std::sync::OnceLock;
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::JobObjects::{
        AssignProcessToJobObject, CreateJobObjectW, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE, JOBOBJECT_EXTENDED_LIMIT_INFORMATION,
        JobObjectExtendedLimitInformation, SetInformationJobObject,
    };
    use windows_sys::Win32::System::Threading::{OpenProcess, PROCESS_SET_QUOTA, PROCESS_TERMINATE};

    // The job lives as long as the process: its handle is never closed, the system closes it as the process ends.
    static JOB: OnceLock<usize> = OnceLock::new();
    let job = *JOB.get_or_init(|| unsafe {
        let job = CreateJobObjectW(std::ptr::null(), std::ptr::null());
        if job.is_null() {
            return 0;
        }
        let mut limits: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = std::mem::zeroed();
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(
            job,
            JobObjectExtendedLimitInformation,
            &limits as *const _ as *const _,
            std::mem::size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32,
        );
        job as usize
    });
    if job == 0 {
        return 0;
    }
    let process = unsafe { OpenProcess(PROCESS_SET_QUOTA | PROCESS_TERMINATE, 0, pid) };
    if process.is_null() {
        return 0;
    }
    let done = unsafe { AssignProcessToJobObject(job as _, process) };
    unsafe { CloseHandle(process) };
    i32::from(done != 0)
}

#[cfg(not(windows))]
pub fn assign(_pid: u32) -> i32 {
    -1
}

/// Holds this process, and every process it starts from now on, to `bytes` of memory together (Windows): this
/// process goes in a job of its own with that limit, and what it starts begins inside it (the browser engine, and the
/// processes the engine starts, its own sandbox jobs nested within). Past it, what any of them asks for is refused.
/// A process cannot leave its job: this lasts as long as the process. 1 done, 0 not, -1 not on this system.
#[cfg(windows)]
pub fn cap_this_process(bytes: u64) -> i32 {
    use windows_sys::Win32::System::JobObjects::{
        AssignProcessToJobObject, CreateJobObjectW, JOB_OBJECT_LIMIT_JOB_MEMORY, JOBOBJECT_EXTENDED_LIMIT_INFORMATION,
        JobObjectExtendedLimitInformation, SetInformationJobObject,
    };
    use windows_sys::Win32::System::Threading::GetCurrentProcess;
    if bytes == 0 {
        return 0;
    }
    unsafe {
        let job = CreateJobObjectW(std::ptr::null(), std::ptr::null());
        if job.is_null() {
            return 0;
        }
        let mut limits: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = std::mem::zeroed();
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_JOB_MEMORY;
        limits.JobMemoryLimit = bytes as usize;
        let set = SetInformationJobObject(
            job,
            JobObjectExtendedLimitInformation,
            &limits as *const _ as *const _,
            std::mem::size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32,
        );
        // the job's handle is never closed: it holds as long as this process
        i32::from(set != 0 && AssignProcessToJobObject(job, GetCurrentProcess()) != 0)
    }
}

#[cfg(not(windows))]
pub fn cap_this_process(_bytes: u64) -> i32 {
    -1
}
