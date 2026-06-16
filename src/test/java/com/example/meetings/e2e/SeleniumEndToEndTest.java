package com.example.meetings.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:selenium-e2e;MODE=LEGACY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.base-url=http://localhost"
})
class SeleniumEndToEndTest {

    @LocalServerPort
    private int port;
    private WebDriver driver;

    @AfterEach
    void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void userRegistersLogsInAndCreatesMeeting() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        driver = new ChromeDriver(options);

        driver.get(baseUrl() + "/register");
        driver.findElement(By.name("username")).sendKeys("selenium");
        driver.findElement(By.name("email")).sendKeys("selenium@example.com");
        driver.findElement(By.name("password")).sendKeys("secret");
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        wait.until(webDriver -> webDriver.getCurrentUrl().contains("/login?registered"));

        driver.findElement(By.name("username")).sendKeys("selenium");
        driver.findElement(By.name("password")).sendKeys("secret");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        wait.until(webDriver -> webDriver.getCurrentUrl().contains("/calendar"));
        assertThat(driver.getPageSource()).contains("Signed in as");

        wait.until(webDriver -> webDriver.findElement(By.linkText("Propose a meeting")));
        driver.get(baseUrl() + "/meetings/new");
        wait.until(webDriver -> webDriver.getCurrentUrl().contains("/meetings/new"));
        WebElement title = wait.until(webDriver -> webDriver.findElement(By.name("title")));
        WebElement description = wait.until(webDriver -> webDriver.findElement(By.name("description")));
        WebElement start = wait.until(webDriver -> webDriver.findElement(By.name("start")));
        WebElement end = wait.until(webDriver -> webDriver.findElement(By.name("end")));
        WebElement submit = wait.until(webDriver ->
                webDriver.findElement(By.cssSelector("form[action='/meetings/new'] button[type='submit']")));

        setInputValue(title, "Browser-created meeting");
        setInputValue(description, "Created through Selenium");
        setInputValue(start, "2026-06-01T10:00");
        setInputValue(end, "2026-06-01T11:00");
        assertThat(title.getDomProperty("value")).isEqualTo("Browser-created meeting");
        assertThat(start.getDomProperty("value")).isEqualTo("2026-06-01T10:00");
        assertThat(end.getDomProperty("value")).isEqualTo("2026-06-01T11:00");
        requestSubmit(submit);

        try {
            wait.until(webDriver -> webDriver.getCurrentUrl().contains("/calendar"));
        } catch (TimeoutException ex) {
            throw new AssertionError("Expected redirect to /calendar after proposing a meeting, but current URL was "
                    + driver.getCurrentUrl() + " and page source was: " + driver.getPageSource(), ex);
        }
        assertThat(driver.getPageSource()).contains("Browser-created meeting");
        assertThat(driver.getPageSource()).contains("confirmed");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void setInputValue(WebElement element, String value) {
        ((JavascriptExecutor) driver).executeScript("""
                arguments[0].value = arguments[1];
                arguments[0].dispatchEvent(new Event('input', { bubbles: true }));
                arguments[0].dispatchEvent(new Event('change', { bubbles: true }));
                """, element, value);
    }

    private void requestSubmit(WebElement submit) {
        Boolean valid = (Boolean) ((JavascriptExecutor) driver).executeScript("""
                const form = arguments[0].form;
                if (!form.checkValidity()) {
                    form.reportValidity();
                    return false;
                }
                form.requestSubmit(arguments[0]);
                return true;
                """, submit);
        assertThat(valid).isTrue();
    }
}
