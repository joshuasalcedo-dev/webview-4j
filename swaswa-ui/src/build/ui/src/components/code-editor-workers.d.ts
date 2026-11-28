// Vite's "?worker" imports (the code editor's Monaco helpers): the default export makes a new web worker.
declare module "*?worker" {
  const WorkerConstructor: new () => Worker
  export default WorkerConstructor
}

// "?worker&inline": the same, with the worker's code inside the module (so the published package needs no worker files)
declare module "*?worker&inline" {
  const WorkerConstructor: new () => Worker
  export default WorkerConstructor
}
