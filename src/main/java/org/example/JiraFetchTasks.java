package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraFetchTasks {
  private static final Logger log = LoggerFactory.getLogger(JiraFetchTasks.class);

  private static final String JIRA_URL = System.getenv("JIRA_DOMAIN");
  private static final String USERNAME = System.getenv("JIRA_EMAIL");
  private static final String API_TOKEN = System.getenv("JIRA_TOKEN");

  public static void main(String[] args) throws IOException {
    var workLogs = fetchWorkLogsFromLast24Hours();
    log.info("WorkLogs :");
    for (var workLog : workLogs) {
      log.info("{}", workLog.summary());
    }
    Map<String, List<WorkLog>> authorWorkLogs = new HashMap<>();
    Map<String, List<WorkLog>> issuesWorkLogs = new HashMap<>();
    for (var worklog : workLogs) {
      authorWorkLogs.computeIfAbsent(worklog.author(), k -> new ArrayList<>()).add(worklog);
      issuesWorkLogs.computeIfAbsent(worklog.issueKey(), k -> new ArrayList<>()).add(worklog);
    }
    for (var logs : authorWorkLogs.entrySet()) {
      var author = logs.getKey();
      var workedOnTaskWithMinPriority =
          logs.getValue().stream().min(Comparator.comparingInt(WorkLog::priority)).orElseThrow();
      log.info("Lowest priority issue worked on task : {}", workedOnTaskWithMinPriority.summary());
      var issues = fetchIssuesByPriorityAndRank(author);
      for (var issue : issues) {
        if (!issuesWorkLogs.containsKey(issue.issueKey())) {
          log.info("Found a issue with lower priority : {} {}", author, issue.summary());
          return;
        }
        if (issue.issueKey().equals(workedOnTaskWithMinPriority.issueKey())) {
          break;
        }
      }
      log.info("No lower priority issue found");
    }
  }

  public static List<WorkLog> fetchWorkLogsFromLast24Hours() throws IOException {
    var jql = URLEncoder.encode("worklogDate >= -1d", StandardCharsets.UTF_8);
    return fetchWorkLogs(jql);
  }

  private static List<WorkLog> fetchWorkLogs(String jql) throws IOException {
    var searchUrl =
        JIRA_URL + "/rest/api/2/search?jql=" + jql + "&fields=worklog,summary,priority,ranks";
    var request = new HttpGet(searchUrl);
    var auth = USERNAME + ":" + API_TOKEN;
    var encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.ISO_8859_1));
    var authHeader = "Basic " + new String(encodedAuth);
    request.setHeader("Authorization", authHeader);

    List<WorkLog> workLogs = new ArrayList<>();
    try (var httpClient = HttpClients.createDefault()) {
      return httpClient.execute(
          request,
          response -> {
            var responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            var jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            var issues = jsonResponse.getAsJsonArray("issues");

            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

            for (var i = 0; i < issues.size(); i++) {
              var issue = issues.get(i).getAsJsonObject();
              var fields = issue.getAsJsonObject("fields");
              var worklog = fields.getAsJsonObject("worklog");
              var worklogsArray = worklog.getAsJsonArray("worklogs");
              var priorityObj = fields.getAsJsonObject("priority");

              for (var j = 0; j < worklogsArray.size(); j++) {
                var worklogEntry = worklogsArray.get(j).getAsJsonObject();
                var started = worklogEntry.get("started").getAsString();
                var startedDate = LocalDateTime.parse(started, formatter);

                if (startedDate.isAfter(LocalDateTime.now().minusDays(1))) {
                  var issueKey = issue.get("key").getAsString();
                  var summary = fields.get("summary").getAsString();
                  var author =
                      worklogEntry.getAsJsonObject("author").get("displayName").getAsString();
                  var timeSpent = worklogEntry.get("timeSpent").getAsString();
                  var priority =
                      priorityObj.get("id").getAsInt(); // Extract numerical priority value
                  workLogs.add(
                      new WorkLog(issueKey, summary, author, started, timeSpent, priority, null));
                }
              }
            }
            return workLogs;
          });
    }
  }

  public static List<Issue> fetchIssuesByPriorityAndRank(String author) throws IOException {
    String jql =
        URLEncoder.encode(
            "assignee=\"" + author + "\" ORDER BY priority DESC, rank ASC", StandardCharsets.UTF_8);
    String searchUrl = JIRA_URL + "/rest/api/2/search?jql=" + jql + "&fields=summary,priority";

    HttpGet request = new HttpGet(searchUrl);
    String auth = USERNAME + ":" + API_TOKEN;
    byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.ISO_8859_1));
    String authHeader = "Basic " + new String(encodedAuth);
    request.setHeader("Authorization", authHeader);

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      return httpClient.execute(
          request,
          response -> {
            String responseBody =
                EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray issuesJsonArray = jsonResponse.getAsJsonArray("issues");

            List<Issue> issues = new ArrayList<>();

            for (int i = 0; i < issuesJsonArray.size(); i++) {
              JsonObject issueJsonObject = issuesJsonArray.get(i).getAsJsonObject();
              String issueKey = issueJsonObject.get("key").getAsString();
              JsonObject fields = issueJsonObject.getAsJsonObject("fields");
              String summary = fields.get("summary").getAsString();
              int priority = fields.getAsJsonObject("priority").get("id").getAsInt();

              Issue issue = new Issue(issueKey, summary, priority);
              issues.add(issue);
            }
            return issues;
          });
    }
  }
}
