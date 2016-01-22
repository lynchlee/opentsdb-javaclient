package im.baas.tsdb.client;

import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.*;
import net.opentsdb.utils.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Description for Class TestClient
 *
 * @author Lynch Lee<Lynch.lee9527@gmail.com>
 * @version 2016-01-23.
 */
public class QueryClient {

    public static void main(String[] args) throws Exception {
        // Create a config object with a path to the file for parsing. Or manually
        // override settings.
        // e.g. config.overrideConfig("tsd.storage.hbase.zk_quorum", "localhost");
//        final Config config = new Config("Classpath:opentsdb.conf")
        final Config config = new Config(false);
        config.overrideConfig("tsd.network.port", "4242");
        config.overrideConfig("tsd.http.staticroot", "/usr/share/opentsdb/static");
        config.overrideConfig("tsd.http.cachedir", "/tmp/opentsdb");
        config.overrideConfig("tsd.core.auto_create_metrics", "true");
        config.overrideConfig("tsd.core.meta.enable_tsuid_incrementing", "true");
        config.overrideConfig("tsd.storage.hbase.data_table", "tsdb");
        config.overrideConfig("tsd.storage.hbase.uid_table", "tsdb-uid");
        config.overrideConfig("tsd.storage.hbase.zk_quorum", "localhost:2181");
        config.overrideConfig("tsd.storage.fix_duplicates", "true");

        final TSDB tsdb = new TSDB(config);

        // main query
        final TSQuery query = new TSQuery();
        // use any string format from
        //http://opentsdb.net/docs/build/html/user_guide/query/dates.html
        query.setStart("1h-ago");
        // Optional: set other global query params

        // at least one sub query required. This is where you specify the metric and tags
        final TSSubQuery sub_query = new TSSubQuery();
        sub_query.setMetric("tsd.hbase.rpcs");

        // tags are optional but you can create and populate a map
        final HashMap<String, String> tags = new HashMap<>(1);
        tags.put("type", "put");
        sub_query.setTags(tags);

        // you do have to set an aggregator. Just provide the name as a string
        sub_query.setAggregator("avg");

        // IMPORTANT: don't forget to add the sub_query
        final ArrayList<TSSubQuery> sub_queries = new ArrayList<>(1);
        sub_queries.add(sub_query);
        query.setQueries(sub_queries);

        // make sure the query is valid. This will throw exceptions if something
        // is missing
        query.validateAndSetQuery();

        // compile the queries into TsdbQuery objects behind the scenes
        Query[] tsdbqueries = query.buildQueries(tsdb);

        // create some arrays for storing the results and the async calls
        final int nqueries = tsdbqueries.length;
        final ArrayList<DataPoints[]> results =
                new ArrayList<>(nqueries);
        final ArrayList<Deferred<DataPoints[]>> deferreds =
                new ArrayList<>(nqueries);

        // this executes each of the sub queries asynchronously and puts the
        // deferred in an array so we can wait for them to complete.
        for (int i = 0; i < nqueries; i++) {
            deferreds.add(tsdbqueries[i].runAsync());
        }

        // This is a required callback class to store the results after each
        // query has finished
        class QueriesCB implements Callback<Object, ArrayList<DataPoints[]>> {
            public Object call(final ArrayList<DataPoints[]> query_results)
                    throws Exception {
                results.addAll(query_results);
                return null;
            }
        }

        // this will cause the calling thread to wait until ALL of the queries
        // have completed.
        Deferred.groupInOrder(deferreds).addCallback(new QueriesCB()).joinUninterruptibly();

        // now all of the results are in so we just iterate over each set of
        // results and do any processing necessary.
        for (final DataPoints[] data_sets : results) {
            for (final DataPoints data : data_sets) {
                System.out.print(data.metricName());
                Map<String, String> resolved_tags = data.getTags();
                for (final Map.Entry<String, String> pair : resolved_tags.entrySet()) {
                    System.out.print(" " + pair.getKey() + "=" + pair.getValue());
                }
                System.out.print("\n");

                for (DataPoint dp : data) {
                    System.out.println("  " + dp.timestamp() + " " +
                            (dp.isInteger() ? dp.longValue() : dp.doubleValue()));
                }

                System.out.println("");
            }
        }
    }

}
