package com.payments.tests;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;
import java.time.Duration;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentDashboardTest {

    private static WebDriver driver;
    private static WebDriverWait wait;

    private static final String DASHBOARD_URL = System.getProperty("dashboard.url", "http://payment-dashboard:80");
    private static final String SELENIUM_URL = System.getProperty("selenium.url", "http://selenium:4444/wd/hub");

    @BeforeAll
    static void setUp() throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        driver = new RemoteWebDriver(new URL(SELENIUM_URL), options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    void shouldLoadDashboard() {
        driver.get(DASHBOARD_URL);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("h1")));
        String title = driver.findElement(By.tagName("h1")).getText();
        Assertions.assertEquals("Payment Simulator Dashboard", title);
    }

    @Test
    @Order(2)
    void shouldSubmitPaymentAndShowPending() {
        driver.get(DASHBOARD_URL);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("sender")));

        driver.findElement(By.name("sender")).sendKeys("Alice");
        driver.findElement(By.name("receiver")).sendKeys("Bob");
        driver.findElement(By.name("amount")).sendKeys("100");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Wait for payment to appear in the list
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("tbody")));
        WebElement tbody = driver.findElement(By.tagName("tbody"));
        Assertions.assertTrue(tbody.getText().contains("Alice"));
    }

    @Test
    @Order(3)
    void shouldShowApprovedStatusForSmallPayment() {
        driver.get(DASHBOARD_URL);

        // Wait for status to transition from PENDING to APPROVED (polling every 3s by the dashboard)
        wait.until(d -> {
            d.navigate().refresh();
            try {
                WebElement tbody = d.findElement(By.tagName("tbody"));
                return tbody.getText().contains("APPROVED");
            } catch (NoSuchElementException e) {
                return false;
            }
        });

        WebElement tbody = driver.findElement(By.tagName("tbody"));
        Assertions.assertTrue(tbody.getText().contains("APPROVED"));
    }

    @Test
    @Order(4)
    void shouldShowFlaggedStatusForLargePayment() {
        driver.get(DASHBOARD_URL);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("sender")));

        driver.findElement(By.name("sender")).sendKeys("Charlie");
        driver.findElement(By.name("receiver")).sendKeys("Dave");
        driver.findElement(By.name("amount")).sendKeys("60000");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Wait for FLAGGED status
        wait.until(d -> {
            d.navigate().refresh();
            try {
                WebElement tbody = d.findElement(By.tagName("tbody"));
                return tbody.getText().contains("FLAGGED");
            } catch (NoSuchElementException e) {
                return false;
            }
        });

        WebElement tbody = driver.findElement(By.tagName("tbody"));
        Assertions.assertTrue(tbody.getText().contains("FLAGGED"));
    }
}
