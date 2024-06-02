package org.example;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.classic.methods.HttpGet;
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
    var worklogs = fetchWorkLogsFromLast24Hours();
    var authorWorklogs = worklogs.stream().collect(Collectors.groupingBy(WorkLog::author));
    for (var logs : authorWorklogs.entrySet()) {
      var author = logs.getKey();
      var workedOnTaskWithMaxPriority =
          logs.getValue().stream().min(Comparator.comparingInt(WorkLog::priority)).orElseThrow();
      log.info("Worked on task with highest priority : {}", workedOnTaskWithMaxPriority.summary());
      int maxPriority = workedOnTaskWithMaxPriority.priority();
      if (checkForHighPriorityTasks(author, maxPriority)) {
        log.warn("Task with higher priority found for author : {}", author);
      } else {
        log.info("No task with higher priority");
      }
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

  public static boolean checkForHighPriorityTasks(String author, int providedPriority)
      throws IOException {
    var jql =
        URLEncoder.encode(
            "assignee=\""
                + author
                + "\" AND priority > "
                + providedPriority
                + " AND status='To Do'",
            StandardCharsets.UTF_8);
    var searchUrl = JIRA_URL + "/rest/api/2/search?jql=" + jql;
    var request = new HttpGet(searchUrl);
    var auth = USERNAME + ":" + API_TOKEN;
    var encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.ISO_8859_1));
    var authHeader = "Basic " + new String(encodedAuth);
    request.setHeader("Authorization", authHeader);
    try (var httpClient = HttpClients.createDefault()) {
      return httpClient.execute(
          request,
          response -> {
            var responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            var jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            var totalIssues = jsonResponse.get("total").getAsInt();
            return totalIssues > 0;
          });
    }
  }
}
