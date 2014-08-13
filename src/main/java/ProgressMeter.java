import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

public class ProgressMeter {

  static class Delta {

    Delta(final long ops, final long time, final long latency) {
      this.ops = ops;
      this.time = time;
      this.latency = latency;
    }

    public final long ops;
    public final long time;
    public final long latency;
  }

  private long lastRows = 0;
  private long lastTime = System.nanoTime();
  private long lastLatency = 0;
  private final long interval = 1000;

  final private String unit;

  final private AtomicLong latency = new AtomicLong();
  final private AtomicLong operations = new AtomicLong();

  final private ArrayDeque<Delta> deltas = new ArrayDeque<Delta>();

  private volatile boolean run = true;

  private final Thread worker;

  public ProgressMeter() {
    this("ops");
  }

  public ProgressMeter(final String unit) {
    this.unit = unit;
    worker = new Thread(new Runnable() {
      public void run() {
        while (run) {
          try {
            Thread.sleep(interval);
          } catch (InterruptedException e) {
            continue;
          }
          progress();
        }
      }
    });
    worker.start();
  }

  private void progress() {
    final long count = this.operations.get();
    final long time = System.nanoTime();
    final long latency = this.latency.get();

    final long delta = count - lastRows;
    final long deltaTime = time - lastTime;
    final long deltaLatency = latency - lastLatency;

    deltas.add(new Delta(delta, deltaTime, deltaLatency));

    if (deltas.size() > 10) {
      deltas.pop();
    }

    long opSum = 0;
    long timeSum = 0;
    long latencySum = 0;

    for (final Delta d : deltas) {
      timeSum += d.time;
      opSum += d.ops;
      latencySum += d.latency;
    }

    final long operations = deltaTime == 0 ? 0 : 1000000000 * delta / deltaTime;
    final long averagedOperations = timeSum == 0 ? 0 : 1000000000 * opSum / timeSum;
    final double averageLatency = opSum == 0 ? 0 : latencySum / (1000000.d * opSum);

    System.out.printf("%,10d (%,10d) %s/s. %,10.9f ms average latency. %,10d %s total.\n",
                      operations, averagedOperations, unit, averageLatency, count, unit);
    System.out.flush();

    lastRows = count;
    lastTime = time;
    lastLatency = latency;
  }

  public void finish() {
    run = false;
    worker.interrupt();
    try {
      worker.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    progress();
  }

  public void inc(final long ops, final long latency) {
    this.operations.addAndGet(ops);
    this.latency.addAndGet(latency);
  }
}
