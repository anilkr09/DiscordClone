// Timers on the page are slowed to once a minute in background tabs, which would let presence lapse.
// A worker's timer keeps its pace.
setInterval(() => postMessage("tick"), 10_000);
