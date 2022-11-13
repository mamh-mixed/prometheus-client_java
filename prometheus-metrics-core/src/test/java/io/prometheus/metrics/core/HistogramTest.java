package io.prometheus.metrics.core;


import io.prometheus.metrics.exemplars.CounterExemplarSampler;
import io.prometheus.metrics.exemplars.HistogramExemplarSampler;
import io.prometheus.metrics.model.Exemplar;
import io.prometheus.metrics.model.ExplicitBucket;
import io.prometheus.metrics.model.ExplicitBucketsHistogramSnapshot;
import io.prometheus.metrics.model.ExplicitBucketsHistogramSnapshot.ExplicitBucketsHistogramData;
import io.prometheus.metrics.model.Labels;
import io.prometheus.metrics.observer.DistributionObserver;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.prometheus.metrics.core.TestUtil.assertExemplarEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HistogramTest {

  /*
  CollectorRegistry registry;
  Histogram noLabels, labels;

  @Rule
  public final ExpectedException thrown = none();

  @Before
  public void setUp() {
    registry = new CollectorRegistry();
    noLabels = Histogram.build().name("nolabels").help("help").register(registry);
    labels = Histogram.build().name("labels").help("help").labelNames("l").register(registry);
  }

  @After
  public void tearDown() {
    SimpleTimer.defaultTimeProvider = new SimpleTimer.TimeProvider();
  }

  private double getCount() {
    return getCount("nolabels");
  }

  private double getCount(String name) {
    return registry.getSampleValue(name + "_count").doubleValue();
  }

  private double getSum() {
    return getSum("nolabels");
  }

  private double getSum(String name) {
    return registry.getSampleValue(name + "_sum").doubleValue();
  }

  private double getBucket(double b) {
    return getBucket(b, "nolabels");
  }

  private double getBucket(double b, String name) {
    return registry.getSampleValue(name + "_bucket",
        new String[]{"le"},
        new String[]{Collector.doubleToGoString(b)}).doubleValue();
  }

   */

  private ExplicitBucketsHistogramData getData(Histogram histogram, String... labels) {
    return ((ExplicitBucketsHistogramSnapshot) histogram.collect()).getData().stream()
            .filter(d -> d.getLabels().equals(Labels.of(labels)))
            .findAny()
            .orElseThrow(() -> new RuntimeException("histogram with labels " + labels + " not found"));
  }

  private ExplicitBucket getBucket(Histogram histogram, double le, String... labels) {
    return getData(histogram, labels).getBuckets().stream()
            .filter(b -> b.getUpperBound() == le)
            .findAny()
            .orElseThrow(() -> new RuntimeException("bucket with le=" + le + " not found."));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalLabelNameHistogramBuilder() {
    Histogram.newBuilder().withLabelNames("label", "le");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalLabelNameExplicitBucketsHistogramBuilder() {
    Histogram.newBuilder().withDefaultBuckets().withLabelNames("label", "le");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalLabelNameConstLabels() {
    Histogram.newBuilder().withConstLabels(Labels.of("label1", "value1", "le", "0.3"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalLabelNamePrefix() {
    Histogram.newBuilder().withLabelNames("__hello");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalLabelNameDot() {
    // The Prometheus team are investigating to allow dots in future Prometheus versions, but for now it's invalid.
    // The reason is that you cannot use illegal label names in the Prometheus query language.
    Histogram.newBuilder().withLabelNames("http.status");
  }

  @Test(expected = RuntimeException.class)
  public void testNoName() {
    Histogram.newBuilder().withDefaultBuckets().build();
  }

  @Test(expected = RuntimeException.class)
  public void testIllegalNameHistogramBuilder() {
    Histogram.newBuilder().withName("server.durations");
  }

  @Test(expected = RuntimeException.class)
  public void testIllegalNameExplicitBucketsHistogramBuilder() {
    Histogram.newBuilder().withBuckets(0, 1, 2).withName("server.durations");
  }

  @Test(expected = RuntimeException.class)
  public void testNullName() {
    Histogram.newBuilder().withDefaultBuckets().withName(null);
  }

  @Test
  public void testDuplicateBuckets() {
    Histogram histogram = Histogram.newBuilder().withName("test").withBuckets(0, 3, 17, 3, 21).build();
    List<Double> upperBounds = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getUpperBound)
            .collect(Collectors.toList());
    Assert.assertEquals(Arrays.asList(0.0, 3.0, 17.0, 21.0, Double.POSITIVE_INFINITY), upperBounds);
  }

  @Test
  public void testUnsortedBuckets() {
    Histogram histogram = Histogram.newBuilder().withBuckets(0.2, 0.1).withName("test").build();
    List<Double> upperBounds = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getUpperBound)
            .collect(Collectors.toList());
    Assert.assertEquals(Arrays.asList(0.1, 0.2, Double.POSITIVE_INFINITY), upperBounds);
  }

  @Test
  public void testEmptyBuckets() {
    Histogram histogram = Histogram.newBuilder().withBuckets().withName("test").build();
    List<Double> upperBounds = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getUpperBound)
            .collect(Collectors.toList());
    Assert.assertEquals(Collections.singletonList(Double.POSITIVE_INFINITY), upperBounds);
  }

  @Test
  public void testBucketsIncludePositiveInfinity() {
    Histogram histogram = Histogram.newBuilder().withBuckets(0.01, 0.1, 1.0, Double.POSITIVE_INFINITY).withName("test").build();
    List<Double> upperBounds = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getUpperBound)
            .collect(Collectors.toList());
    Assert.assertEquals(Arrays.asList(0.01, 0.1, 1.0, Double.POSITIVE_INFINITY), upperBounds);
  }

  @Test
  public void testLinearBuckets() {
    Histogram histogram = Histogram.newBuilder()
            .withName("test")
            .withLinearBuckets(0.1, 0.1, 10)
            .build();
    List<Double> upperBounds = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getUpperBound)
            .collect(Collectors.toList());
    Assert.assertEquals(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, Double.POSITIVE_INFINITY), upperBounds);
  }

  @Test
  public void testExponentialBuckets() {
    Histogram histogram = Histogram.newBuilder()
            .withExponentialBuckets(2, 2.5, 3)
            .withName("test")
            .build();
    List<Double> upperBounds = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getUpperBound)
            .collect(Collectors.toList());
    assertEquals(Arrays.asList(2.0, 5.0, 12.5, Double.POSITIVE_INFINITY), upperBounds);
  }

  @Test(expected = RuntimeException.class)
  public void testBucketsIncludeNaN() {
    Histogram.newBuilder().withBuckets(0.01, 0.1, 1.0, Double.NaN);
  }

  @Test
  public void testNoLabelsDefaultZeroValue() {
    Histogram noLabels = Histogram.newBuilder().withName("test").withDefaultBuckets().build();
    assertEquals(0.0, getBucket(noLabels, 0.005).getCumulativeCount(), 0.000001);
    assertEquals(0, getData(noLabels).getCount());
    assertEquals(0.0, getData(noLabels).getSum(), 0.000001);
  }

  @Test
  public void testObserve() {
    Histogram noLabels = Histogram.newBuilder()
            .withName("test")
            .withDefaultBuckets()
            .build();
    noLabels.observe(2);
    assertEquals(1, getData(noLabels).getCount());
    assertEquals(2.0, getData(noLabels).getSum(), .001);
    assertEquals(0.0, getBucket(noLabels, 1).getCumulativeCount(), .001);
    assertEquals(1.0, getBucket(noLabels, 2.5).getCumulativeCount(), .001);
    noLabels.observe(4);
    assertEquals(2.0, getData(noLabels).getCount(), .001);
    assertEquals(6.0, getData(noLabels).getSum(), .001);
    assertEquals(0.0, getBucket(noLabels, 1).getCumulativeCount(), .001);
    assertEquals(1.0, getBucket(noLabels, 2.5).getCumulativeCount(), .001);
    assertEquals(2.0, getBucket(noLabels, 5).getCumulativeCount(), .001);
    assertEquals(2.0, getBucket(noLabels, 7.5).getCumulativeCount(), .001);
    assertEquals(2.0, getBucket(noLabels, 10).getCumulativeCount(), .001);
    assertEquals(2.0, getBucket(noLabels, Double.POSITIVE_INFINITY).getCumulativeCount(), .001);
  }

  @Test
  // See https://github.com/prometheus/client_java/issues/646
  public void testNegativeAmount() {
    Histogram histogram = Histogram.newBuilder()
        .withName("histogram")
        .withHelp("test histogram for negative values")
        .withBuckets(-10, -5, 0, 5, 10)
        .build();
    double expectedCount = 0;
    double expectedSum = 0;
    for (int i=10; i>=-11; i--) {
      histogram.observe(i);
      expectedCount++;
      expectedSum += i;
      assertEquals(expectedSum, getData(histogram).getSum(), .001);
      assertEquals(expectedCount, getData(histogram).getCount(), .001);
    }
    List<Long> expectedBucketCounts = Arrays.asList(2L, 7L, 12L, 17L, 22L, 22L); // buckets -10, -5, 0, 5, 10, +Inf
    List<Long> actualBucketCounts = getData(histogram).getBuckets().stream()
            .map(ExplicitBucket::getCumulativeCount)
            .collect(Collectors.toList());
    assertEquals(expectedBucketCounts, actualBucketCounts);
  }

  @Test
  public void testBoundaryConditions() {
    Histogram histogram = Histogram.newBuilder()
            .withName("test")
            .withDefaultBuckets()
            .build();
    histogram.observe(2.5);
    assertEquals(0, getBucket(histogram, 1).getCumulativeCount());
    assertEquals(1, getBucket(histogram, 2.5).getCumulativeCount());

    histogram.observe(Double.POSITIVE_INFINITY);
    assertEquals(0, getBucket(histogram, 1).getCumulativeCount());
    assertEquals(1, getBucket(histogram, 2.5).getCumulativeCount());
    assertEquals(1, getBucket(histogram, 5).getCumulativeCount());
    assertEquals(1, getBucket(histogram, 7.5).getCumulativeCount());
    assertEquals(1, getBucket(histogram, 10).getCumulativeCount());
    assertEquals(2, getBucket(histogram, Double.POSITIVE_INFINITY).getCumulativeCount());
  }

  @Test
  public void testObserveWithLabels() {
    Histogram histogram = Histogram.newBuilder()
            .withDefaultBuckets()
            .withName("test")
            .withConstLabels(Labels.of("env", "prod"))
            .withLabelNames("path", "status")
            .build();
    histogram.withLabels("/hello", "200").observe(0.11);
    histogram.withLabels("/hello", "200").observe(0.2);
    histogram.withLabels("/hello", "500").observe(0.19);
    ExplicitBucketsHistogramData data200 = getData(histogram, "env", "prod", "path", "/hello", "status", "200");
    ExplicitBucketsHistogramData data500 = getData(histogram, "env", "prod", "path", "/hello", "status", "500");
    assertEquals(2, data200.getCount());
    assertEquals(0.31, data200.getSum(), 0.0000001);
    assertEquals(1, data500.getCount());
    assertEquals(0.19, data500.getSum(), 0.0000001);
    histogram.withLabels("/hello", "200").observe(0.13);
    data200 = getData(histogram, "env", "prod", "path", "/hello", "status", "200");
    data500 = getData(histogram, "env", "prod", "path", "/hello", "status", "500");
    assertEquals(3, data200.getCount());
    assertEquals(0.44, data200.getSum(), 0.0000001);
    assertEquals(1, data500.getCount());
    assertEquals(0.19, data500.getSum(), 0.0000001);
  }

  @Test
  public void testObserveMultithreaded() throws InterruptedException, ExecutionException, TimeoutException {
    // Hard to test concurrency, but let's run a couple of observations in parallel and assert none gets lost.
    Histogram histogram = Histogram.newBuilder()
            .withDefaultBuckets()
            .withName("test")
            .withLabelNames("status")
            .build();
    int nThreads = 8;
    DistributionObserver obs = histogram.withLabels("200");
    ExecutorService executor = Executors.newFixedThreadPool(nThreads);
    CompletionService<List<ExplicitBucketsHistogramSnapshot>> completionService = new ExecutorCompletionService<>(executor);
    CountDownLatch startSignal = new CountDownLatch(nThreads);
    for (int t=0; t<nThreads; t++) {
      completionService.submit(() -> {
        List<ExplicitBucketsHistogramSnapshot> snapshots = new ArrayList<>();
        startSignal.countDown();
        startSignal.await();
        for (int i=0; i<10; i++) {
          for (int j=0; j<1000; j++) {
            obs.observe(1.1);
          }
          snapshots.add((ExplicitBucketsHistogramSnapshot) histogram.collect());
        }
        return snapshots;
      });
    }
    long maxCount = 0;
    for(int i=0; i<nThreads; i++) {
      Future<List<ExplicitBucketsHistogramSnapshot>> future = completionService.take();
      List<ExplicitBucketsHistogramSnapshot> snapshots = future.get(5, TimeUnit.SECONDS);
      long count = 0;
      for (ExplicitBucketsHistogramSnapshot snapshot : snapshots) {
        Assert.assertEquals(1, snapshot.getData().size());
        ExplicitBucketsHistogramData data = snapshot.getData().stream().findFirst().orElseThrow(RuntimeException::new);
        Assert.assertTrue(data.getCount() >= (count + 1000)); // 1000 own observations plus the ones from other threads
        count = data.getCount();
      }
      if (count > maxCount) {
        maxCount = count;
      }
    }
    Assert.assertEquals(nThreads * 10_000, maxCount); // the last collect() has seen all observations
    Assert.assertEquals(getBucket(histogram, 2.5, "status", "200").getCumulativeCount(), nThreads * 10_000);
    executor.shutdown();
    Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
  }

  @Test
  public void testExemplarSampler() {
    final Exemplar exemplar1 = new Exemplar(1.1, Labels.of("trace_id", "abc", "span_id", "123"), System.currentTimeMillis());
    final Exemplar exemplar2 = new Exemplar(2.1, Labels.of("trace_id", "def", "span_id", "456"), System.currentTimeMillis());
    final Exemplar exemplar3 = new Exemplar(2.2, Labels.of("trace_id", "123", "span_id", "abc"), System.currentTimeMillis());
    final AtomicReference<Integer> callNumber = new AtomicReference<>(0);

    // 1.1, 2.1, 1.3, 2.2

    HistogramExemplarSampler exemplarSampler = (val, from, to, prev) -> {
      switch (callNumber.get()) {
        case 0:
          Assert.assertEquals(1.1, val, 0.00001);
          Assert.assertEquals(1.0, from, 0.00001);
          Assert.assertEquals(2.0, to, 0.00001);
          assertNull(prev);
          return exemplar1;
        case 1:
          Assert.assertEquals(2.1, val,  0.00001);
          Assert.assertEquals(2.0, from, 0.00001);
          Assert.assertEquals(3.0, to, 0.00001);
          assertNull(prev);
          return exemplar2;
        case 2:
          Assert.assertEquals(1.3, val, 0.00001);
          Assert.assertEquals(1.0, from, 0.00001);
          Assert.assertEquals(2.0, to, 0.00001);
          Assert.assertEquals(exemplar1, prev);
          return null;
        case 3:
          Assert.assertEquals(2.2, val, 0.00001);
          Assert.assertEquals(2.0, from, 0.00001);
          Assert.assertEquals(3.0, to, 0.00001);
          Assert.assertEquals(exemplar2, prev);
          return exemplar3;
        default:
          throw new RuntimeException("Unexpected 5th call");
      }
    };
    Histogram histogram = Histogram.newBuilder()
            .withName("test")
            .withBuckets(1.0, 2.0, 3.0)
            .withExemplarSampler(exemplarSampler)
            .build();

    assertNull(getBucket(histogram, 2.0).getExemplar());
    assertNull(getBucket(histogram, 3.0).getExemplar());
    histogram.observe(1.1);
    callNumber.set(callNumber.get() + 1);
    assertExemplarEquals(exemplar1, getBucket(histogram, 2.0).getExemplar());
    assertNull(getBucket(histogram, 3.0).getExemplar());
    histogram.observe(2.1);
    callNumber.set(callNumber.get() + 1);
    assertExemplarEquals(exemplar1, getBucket(histogram, 2.0).getExemplar());
    assertExemplarEquals(exemplar2, getBucket(histogram, 3.0).getExemplar());
    histogram.observe(1.3);
    callNumber.set(callNumber.get() + 1);
    assertExemplarEquals(exemplar1, getBucket(histogram, 2.0).getExemplar());
    assertExemplarEquals(exemplar2, getBucket(histogram, 3.0).getExemplar());
    histogram.observe(2.2);
    callNumber.set(callNumber.get() + 1);
    assertExemplarEquals(exemplar1, getBucket(histogram, 2.0).getExemplar());
    assertExemplarEquals(exemplar3, getBucket(histogram, 3.0).getExemplar());
    histogram.observeWithExemplar(1.4, Labels.of("key1", "value1", "key2", "value2"));
    assertExemplarEquals(new Exemplar(1.4, Labels.of("key1", "value1", "key2", "value2"), System.currentTimeMillis()), getBucket(histogram, 2.0).getExemplar());
    assertExemplarEquals(exemplar3, getBucket(histogram, 3.0).getExemplar());
  }

  /*
  @Test
  public void testObserveWithExemplar() {
    Histogram histogram = Histogram.newBuilder()
            .withName("test")
            .withExemplars()
            .withDefaultBuckets()
            .build();
    Map<String, String> labels = new HashMap<String, String>();
    labels.put("mapKey1", "mapValue1");
    labels.put("mapKey2", "mapValue2");

    histogram.observeWithExemplar(0.5, Labels.of("key", "value"));
    assertExemplar(noLabels, 0.5, "key", "value");

    noLabels.observeWithExemplar(0.5);
    assertExemplar(noLabels, 0.5);

    noLabels.observeWithExemplar(0.5, labels);
    assertExemplar(noLabels, 0.5, "mapKey1", "mapValue1", "mapKey2", "mapValue2");

    // default buckets are {.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10}
    noLabels.observeWithExemplar(2.0, "key1", "value1", "key2", "value2");
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");
    assertExemplar(noLabels, 0.5, "mapKey1", "mapValue1", "mapKey2", "mapValue2");

    noLabels.observeWithExemplar(0.4, new HashMap<String, String>()); // same bucket as 0.5
    assertNoExemplar(noLabels, 0.5);
    assertExemplar(noLabels, 0.4);
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");

    noLabels.observeWithExemplar(2.0, (String[]) null); // should not alter the exemplar
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");

    noLabels.observeWithExemplar(2.0, (Map<String, String>) null); // should not alter the exemplar
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");
  }
   */

  /*


  @Test
  public void testTimer() {
    SimpleTimer.defaultTimeProvider = new SimpleTimer.TimeProvider() {
      long value = (long)(30 * 1e9);
      long nanoTime() {
        value += (long)(10 * 1e9);
        return value;
      }
    };

    double elapsed = noLabels.time(new Runnable() {
      @Override
      public void run() {
        //no op
      }
    });
    assertEquals(10, elapsed, .001);

    int result = noLabels.time(new Callable<Integer>() {
      @Override
      public Integer call() {
        return 123;
      }
    });
    assertEquals(123, result);

    Histogram.Timer timer = noLabels.startTimer();
    elapsed = timer.observeDuration();
    assertEquals(3, getCount(), .001);
    assertEquals(30, getSum(), .001);
    assertEquals(10, elapsed, .001);
  }

  @Test
  public void noLabelsDefaultZeroValue() {
    assertEquals(0.0, getCount(), .001);
    assertEquals(0.0, getSum(), .001);
  }

  private Double getLabelsCount(String labelValue) {
    return registry.getSampleValue("labels_count", new String[]{"l"}, new String[]{labelValue});
  }
  private Double getLabelsSum(String labelValue) {
    return registry.getSampleValue("labels_sum", new String[]{"l"}, new String[]{labelValue});
  }

  @Test
  public void testLabels() {
    assertEquals(null, getLabelsCount("a"));
    assertEquals(null, getLabelsSum("a"));
    assertEquals(null, getLabelsCount("b"));
    assertEquals(null, getLabelsSum("b"));
    labels.labels("a").observe(2);
    assertEquals(1.0, getLabelsCount("a").doubleValue(), .001);
    assertEquals(2.0, getLabelsSum("a").doubleValue(), .001);
    assertEquals(null, getLabelsCount("b"));
    assertEquals(null, getLabelsSum("b"));
    labels.labels("b").observe(3);
    assertEquals(1.0, getLabelsCount("a").doubleValue(), .001);
    assertEquals(2.0, getLabelsSum("a").doubleValue(), .001);
    assertEquals(1.0, getLabelsCount("b").doubleValue(), .001);
    assertEquals(3.0, getLabelsSum("b").doubleValue(), .001);
  }

  @Test
  public void testLeLabelThrows() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Histogram cannot have a label named 'le'.");
    Histogram.build().name("labels").help("help").labelNames("le").create();
  }

  @Test
  public void testObserveWithExemplar() {
    Map<String, String> labels = new HashMap<String, String>();
    labels.put("mapKey1", "mapValue1");
    labels.put("mapKey2", "mapValue2");

    noLabels.observeWithExemplar(0.5, "key", "value");
    assertExemplar(noLabels, 0.5, "key", "value");

    noLabels.observeWithExemplar(0.5);
    assertExemplar(noLabels, 0.5);

    noLabels.observeWithExemplar(0.5, labels);
    assertExemplar(noLabels, 0.5, "mapKey1", "mapValue1", "mapKey2", "mapValue2");

    // default buckets are {.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10}
    noLabels.observeWithExemplar(2.0, "key1", "value1", "key2", "value2");
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");
    assertExemplar(noLabels, 0.5, "mapKey1", "mapValue1", "mapKey2", "mapValue2");

    noLabels.observeWithExemplar(0.4, new HashMap<String, String>()); // same bucket as 0.5
    assertNoExemplar(noLabels, 0.5);
    assertExemplar(noLabels, 0.4);
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");

    noLabels.observeWithExemplar(2.0, (String[]) null); // should not alter the exemplar
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");

    noLabels.observeWithExemplar(2.0, (Map<String, String>) null); // should not alter the exemplar
    assertExemplar(noLabels, 2.0, "key1", "value1", "key2", "value2");
  }

  @Test
  public void testTimeWithExemplar() {
    Map<String, String> labels = new HashMap<String, String>();
    labels.put("mapKey1", "mapValue1");
    labels.put("mapKey2", "mapValue2");

    noLabels.timeWithExemplar(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(15);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    });
    assertExemplar(noLabels, 0.015);

    noLabels.timeWithExemplar(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        Thread.sleep(20);
        return null;
      }
    }, labels);
    assertNoExemplar(noLabels, 0.015);
    assertExemplar(noLabels, 0.02,"mapKey1", "mapValue1", "mapKey2", "mapValue2");
  }

  private void assertExemplar(Histogram histogram, double value, String... labels) {
    List<Collector.MetricFamilySamples> mfs = histogram.collect();
    double lowerBound;
    double upperBound = Double.NEGATIVE_INFINITY;
    for (Sample bucket : mfs.get(0).samples) {
      if (!bucket.name.endsWith("_bucket")) {
        continue;
      }
      lowerBound = upperBound;
      if ("+Inf".equals(findLabelValue(bucket, "le"))) {
        upperBound = Double.POSITIVE_INFINITY;
      } else {
        upperBound = Double.parseDouble(findLabelValue(bucket, "le"));
      }
      if (lowerBound < value && value <= upperBound) {
        Assert.assertNotNull("No exemplar found in bucket [" + lowerBound + ", " + upperBound + "]", bucket.exemplar);
        Assert.assertEquals(value, bucket.exemplar.getValue(), 0.01);
        Assert.assertEquals(labels.length/2, bucket.exemplar.getNumberOfLabels());
        for (int i=0; i<labels.length; i+=2) {
          Assert.assertEquals(labels[i], bucket.exemplar.getLabelName(i/2));
          Assert.assertEquals(labels[i+1], bucket.exemplar.getLabelValue(i/2));
        }
        return;
      }
    }
    throw new AssertionError("exemplar not found in histogram");
  }

  private void assertNoExemplar(Histogram histogram, double value) {
    List<Collector.MetricFamilySamples> mfs = histogram.collect();
    double lowerBound;
    double upperBound = Double.NEGATIVE_INFINITY;
    for (Sample bucket : mfs.get(0).samples) {
      if (!bucket.name.endsWith("_bucket")) {
        continue;
      }
      lowerBound = upperBound;
      if ("+Inf".equals(findLabelValue(bucket, "le"))) {
        upperBound = Double.POSITIVE_INFINITY;
      } else {
        upperBound = Double.parseDouble(findLabelValue(bucket, "le"));
      }
      if (lowerBound < value && value <= upperBound) {
        if (bucket.exemplar != null) {
          Assert.assertNotEquals("expecting no exemplar with value " + value, value, bucket.exemplar.getValue(), 0.0001);
        }
      }
    }
  }

  private String findLabelValue(Sample sample, String labelName) {
    for (int i = 0; i < sample.labelNames.size(); i++) {
      if (sample.labelNames.get(i).equals(labelName)) {
        return sample.labelValues.get(i);
      }
    }
    throw new AssertionError("label " + labelName + " not found in " + sample);
  }

  @Test
  public void testCollect() {
    labels.labels("a").observe(2);
    List<Collector.MetricFamilySamples> mfs = labels.collect();

    ArrayList<Sample> samples = new ArrayList<Sample>();
    ArrayList<String> labelNames = new ArrayList<String>();
    labelNames.add("l");
    ArrayList<String> labelValues = new ArrayList<String>();
    labelValues.add("a");
    ArrayList<String> labelNamesLe = new ArrayList<String>(labelNames);
    labelNamesLe.add("le");
    for (String bucket: new String[]{"0.005", "0.01", "0.025", "0.05", "0.075", "0.1", "0.25", "0.5", "0.75", "1.0"}) {
      ArrayList<String> labelValuesLe = new ArrayList<String>(labelValues);
      labelValuesLe.add(bucket);
      samples.add(new Sample("labels_bucket", labelNamesLe, labelValuesLe, 0.0));
    }
    for (String bucket: new String[]{"2.5", "5.0", "7.5", "10.0", "+Inf"}) {
      ArrayList<String> labelValuesLe = new ArrayList<String>(labelValues);
      labelValuesLe.add(bucket);
      samples.add(new Sample("labels_bucket", labelNamesLe, labelValuesLe, 1.0));
    }
    samples.add(new Sample("labels_count", labelNames, labelValues, 1.0));
    samples.add(new Sample("labels_sum", labelNames, labelValues, 2.0));
    samples.add(new Sample("labels_created", labelNames, labelValues, labels.labels("a").get().created / 1000.0));

    Collector.MetricFamilySamples mfsFixture = new Collector.MetricFamilySamples("labels", Collector.Type.HISTOGRAM, "help", samples);

    assertEquals(1, mfs.size());
    assertEquals(mfsFixture, mfs.get(0));
  }

  @Test
  public void testChildAndValuePublicApi() throws Exception {
    assertTrue(Modifier.isPublic(Histogram.Child.class.getModifiers()));

    final Method getMethod = Histogram.Child.class.getMethod("get");
    assertTrue(Modifier.isPublic(getMethod.getModifiers()));
    assertEquals(Histogram.Child.Value.class, getMethod.getReturnType());

    assertTrue(Modifier.isPublic(Histogram.Child.Value.class.getModifiers()));
    assertTrue(Modifier.isPublic(Histogram.Child.Value.class.getField("sum").getModifiers()));
    assertTrue(Modifier.isPublic(Histogram.Child.Value.class.getField("buckets").getModifiers()));
  }

   */
}
