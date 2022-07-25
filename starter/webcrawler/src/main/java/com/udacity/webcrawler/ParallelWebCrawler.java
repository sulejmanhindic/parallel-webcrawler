package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final int maxDepth;
  private final PageParserFactory pf;
  private final List<Pattern> ignoredUrls;

  @Inject
  ParallelWebCrawler(
          Clock clock,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @TargetParallelism int threadCount,
          @MaxDepth int maxDepth,
          PageParserFactory pf,
          @IgnoredUrls List<Pattern> ignoredUrls) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.maxDepth = maxDepth;
    this.pf = pf;
    this.ignoredUrls = ignoredUrls;
  }

  @Override
  // This method is based on the method crawl of the class SequentialWebCrawler,
  // but adjusted for the use of a parallel web crawler at the start of this method
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    // Here begins the adjusted code for the parallel web crawler
    ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();

    startingUrls.stream().forEach(startingUrl -> pool.invoke(new Crawler(startingUrl, deadline, maxDepth, counts, visitedUrls)));
    // Here ends the adjusted code for the parallel web crawler

    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  // This inner class is based on the method crawlInternal of the class SequentialWebCrawler,
  // but adjusted for the use of a parallel web crawler at the end of the method compute
  public class Crawler extends RecursiveAction {

    private String url;
    private Instant deadline;
    private int maxDepth;
    private ConcurrentMap<String, Integer> counts;
    private ConcurrentSkipListSet<String> visitedUrls;
    private ForkJoinPool pool;

    public Crawler(String url, Instant deadline, int maxDepth, ConcurrentMap<String, Integer> counts, ConcurrentSkipListSet<String> visitedUrls) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
    }

    @Override
    protected void compute() {
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }

      // Here begins the adjusted code for the parallel web crawler to make it thread-safe
      if (!visitedUrls.add(url)) {
        return;
      }
      // Here ends the adjusted code for the parallel web crawler to make it thread-safe

      PageParser.Result result = pf.get(url).parse();

      // Here begins the adjusted code for the parallel web crawler to make it thread-safe
      result.getWordCounts().entrySet()
              .parallelStream()
              .forEach(a -> counts.compute(a.getKey(), (k, v) -> (v != null) ? a.getValue() + v : a.getValue()));
      // Here ends the adjusted code for the parallel web crawler to make it thread-safe

      // Here begins the adjusted code for the parallel web crawler
      Set<Crawler> subsearches = new HashSet<>();
      result
      .getLinks()
      .stream()
      .forEach(subUrl -> subsearches.add(new Crawler(subUrl, deadline, maxDepth - 1, counts, visitedUrls)));

      invokeAll(subsearches);
      // Here ends the adjusted code for the parallel web crawler
    }
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}
