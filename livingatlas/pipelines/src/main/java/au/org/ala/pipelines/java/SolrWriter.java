package au.org.ala.pipelines.java;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

/** A SolrWriter based on {@link org.gbif.pipelines.core.io.ElasticsearchWriter} */
@Slf4j
@Builder
public class SolrWriter<T> {

  private final String zkHost;
  private final String collection;
  private final boolean useSyncMode;
  private final Function<T, SolrInputDocument> indexRequestFn;
  private final ExecutorService executor;
  private final Collection<T> records;
  private final int solrMaxBatchSize;

  @SneakyThrows
  public void write() {

    ModifiableSolrParams params = new ModifiableSolrParams();
    CloseableHttpClient httpClient = HttpClientUtil.createClient(params);

    try (CloudSolrClient client = new CloudSolrClient(zkHost, httpClient)) {

      client.setDefaultCollection(collection);
      List<CompletableFuture<Void>> futures = new ArrayList<>();

      Queue<UpdateRequest> requests = new LinkedList<>();
      requests.add(new UpdateRequest());

      Consumer<T> addIndexRequestFn =
          br ->
              Optional.ofNullable(requests.peek())
                  .ifPresent(req -> req.add(indexRequestFn.apply(br)));

      Consumer<UpdateRequest> clientIndexFn =
          updateRequest -> {
            try {
              NamedList<Object> updateResponse = client.request(updateRequest);
              for (Map.Entry<String, Object> entry : updateResponse) {
                if (entry.getKey().equals("status") && entry.getValue().toString().equals("0")) {
                  // status "0" is equivalent to HTTP 200
                  log.warn("SOLR returned status: " + entry.getValue());
                }
              }
            } catch (Exception ex) {
              log.error(ex.getMessage(), ex);
              throw new RuntimeException(ex.getMessage(), ex);
            }
          };

      Runnable pushIntoSolrFn =
          () ->
              Optional.ofNullable(requests.poll())
                  .filter(req -> req.getDocuments() != null && !req.getDocuments().isEmpty())
                  .ifPresent(
                      req -> {
                        if (useSyncMode) {
                          clientIndexFn.accept(req);
                        } else {
                          futures.add(
                              CompletableFuture.runAsync(
                                  () -> clientIndexFn.accept(req), executor));
                        }
                      });

      // Push requests into ES
      for (T t : records) {
        UpdateRequest peek = requests.peek();
        if (peek != null
            && (peek.getDocuments() == null || peek.getDocuments().size() < solrMaxBatchSize - 1)) {
          addIndexRequestFn.accept(t);
        } else {
          addIndexRequestFn.accept(t);
          pushIntoSolrFn.run();
          requests.add(new UpdateRequest());
        }
      }

      // Final push
      pushIntoSolrFn.run();

      // Wait for all futures
      if (!useSyncMode) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
      }
    }
  }
}