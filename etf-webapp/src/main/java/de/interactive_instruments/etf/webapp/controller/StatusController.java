/**
 * Copyright 2010-2022 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.webapp.controller;

import static de.interactive_instruments.etf.EtfConstants.ETF_TESTDRIVERS_STORAGE_DIR;
import static de.interactive_instruments.etf.webapp.SwaggerConfig.STATUS_TAG_NAME;
import static de.interactive_instruments.etf.webapp.controller.EtfConfig.ETF_TEST_OBJECT_MAX_SIZE;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.sun.management.OperatingSystemMXBean;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import de.interactive_instruments.IFile;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.config.InvalidPropertyException;
import de.interactive_instruments.exceptions.config.MissingPropertyException;
import io.swagger.annotations.*;

/**
 * Controller for reporting the service status
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
@RestController
public class StatusController {

    @Autowired
    private EtfConfig etfConfig;

    private final static String STATUS_DESCRIPTION = "Status MINOR indicates that "
            + "the service encounters an increased workload." +
            " Status MAJOR indicates that framework internal errors "
            + "were thrown during the last 2 minutes.";

    private IFile tdDir;
    private static long startTimeMillis;
    boolean shutDownTriggered = false;
    boolean overloadTriggered = false;

    private final Logger logger = LoggerFactory.getLogger(StatusController.class);

    @JsonPropertyOrder({
            "name",
            "status",
            "heartbeat",
            "willExpireAt",
            "version",
            "uptime",
            "allocatedMemory",
            "presumableFreeMemory",
            "totalSpace",
            "freeSpace",
            "cpuLoad",
            "messages",
    })
    @ApiModel(description = "Extended status information about the service")
    private final static class ExtendedServiceStatus {

        @ApiModelProperty(value = "Service instance name", example = "Validator X")
        private final String name;

        @ApiModelProperty(value = "Service status. "
                + STATUS_DESCRIPTION, example = "GOOD", allowableValues = "STARTING, GOOD, MINOR, MAJOR, MAINTENANCE, SHUTDOWN")
        private final String status;

        @ApiModelProperty(value = "Timestamp in milliseconds, measured between the time the "
                + "test service status was checked and midnight, January 1, 1970 "
                + "UTC(coordinated universal time).", example = "1488469744783")
        private final String heartbeat;

        @ApiModelProperty(value = "Timestamp in milliseconds when the service will examine the "
                + "service status again and provide new information", example = "1488469744783")
        private final String willExpireAt;

        @ApiModelProperty(value = "Service instance version", example = "2.0.0")
        private final String version;

        @ApiModelProperty(value = "Service uptime in seconds", example = "60000")
        private final String uptime;

        @ApiModelProperty(value = "Amount of allocated memory in bytes", example = "2147483648")
        private final String allocatedMemory;

        @ApiModelProperty(value = "Amount of memory that can be freed, in bytes", example = "1073741824")
        private final String presumableFreeMemory;

        @ApiModelProperty(value = "Total disk space in bytes", example = "1099511627776")
        private final String totalSpace;

        @ApiModelProperty(value = "Free disk space in bytes", example = "786432000")
        private final String freeSpace;

        @ApiModelProperty(value = "Returns the recent cpu usage for the service.", example = "786432000")
        private final String cpuLoad;

        @ApiModelProperty(value = "Service warning and/or error messages", example = "[\"Less than 10% RAM available\"]")
        private final List<String> messages;

        private ExtendedServiceStatus(final String name, final String status, final long heartbeat,
                final long willExpireAt, final String version, final long allocatedMemory,
                final long presumableFreeMemory, final long totalSpace, final long freeSpace,
                final String cpuLoad, final List<String> messages) {
            this.name = name;
            this.status = status;
            this.heartbeat = String.valueOf(heartbeat);
            this.willExpireAt = String.valueOf(willExpireAt);
            this.uptime = String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTimeMillis));
            this.version = version;
            this.allocatedMemory = String.valueOf(allocatedMemory);
            this.presumableFreeMemory = String.valueOf(presumableFreeMemory);
            this.totalSpace = String.valueOf(totalSpace);
            this.freeSpace = String.valueOf(freeSpace);
            this.cpuLoad = cpuLoad;
            if (messages != null && !messages.isEmpty()) {
                this.messages = messages;
            } else {
                this.messages = null;
            }
        }
    }

    @PostConstruct
    public void init() throws MissingPropertyException {
        tdDir = etfConfig.getPropertyAsFile(ETF_TESTDRIVERS_STORAGE_DIR);
        startTimeMillis = System.currentTimeMillis();
        mbean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        logger.info("Status controller initialized!");
    }

    private final AtomicReference<HttpHeaders> serviceStatusHeaders = new AtomicReference<>(
            new HttpHeaders() {
                {
                    set("Service-Status", ServiceStatus.STARTING.toString());
                    setLastModified(System.currentTimeMillis());
                    setExpires(System.currentTimeMillis() + 2000);
                }
            });

    private final AtomicReference<ExtendedServiceStatus> serviceStatus = new AtomicReference<>();

    private OperatingSystemMXBean mbean;

    private final static int updateInterval = 20000;
    // 5 GB
    private final static long defaultDiskSpaceAlarm = 5368709120L;

    @Scheduled(fixedDelay = updateInterval)
    public void watch() {

        final long allocatedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        final long presumableFreeMemory = Runtime.getRuntime().maxMemory() - allocatedMemory;

        final List<String> statusWarningMessages = new ArrayList<>();
        ServiceStatus nextStatus = ServiceStatus.GOOD;
        final double usableDiskSpace = ((double) tdDir.getFreeSpace()) / ((double) tdDir.getTotalSpace());
        final double usableMemory = ((double) allocatedMemory) / ((double) Runtime.getRuntime().maxMemory());

        long testObjectMaxSize;
        try {
            testObjectMaxSize = etfConfig.getPropertyAsLong(ETF_TEST_OBJECT_MAX_SIZE);
        } catch (InvalidPropertyException e) {
            // Should never happen
            ExcUtils.suppress(e);
            testObjectMaxSize = 5368709120L;
        }

        final long freeSpace = tdDir.getFreeSpace();
        if (freeSpace < testObjectMaxSize) {
            statusWarningMessages.add("Less than " + FileUtils.byteCountToDisplaySize(testObjectMaxSize) +
                    " disk space available");
            nextStatus = ServiceStatus.MAJOR;
        } else if (freeSpace < defaultDiskSpaceAlarm) {
            statusWarningMessages.add("Less than " + FileUtils.byteCountToDisplaySize(defaultDiskSpaceAlarm) +
                    " disk space available");
            nextStatus = ServiceStatus.MAJOR;
        }
        if (usableDiskSpace < 0.13) {
            statusWarningMessages.add("Less than 13% disk space available");
            if (nextStatus != ServiceStatus.MAJOR) {
                nextStatus = ServiceStatus.MINOR;
            }
        }
        if (usableMemory > 0.90) {
            statusWarningMessages.add("Less than 10% RAM available");
            if (nextStatus != ServiceStatus.MAJOR) {
                nextStatus = ServiceStatus.MINOR;
            }
        }
        if (presumableFreeMemory < 536870912L) {
            statusWarningMessages.add("Less than 512 MB RAM available");
            nextStatus = ServiceStatus.MAJOR;
        }

        final long modified = System.currentTimeMillis();
        final long expires;
        if (nextStatus == ServiceStatus.GOOD) {
            expires = modified + updateInterval * 8;
        } else if (nextStatus == ServiceStatus.MINOR) {
            expires = modified + updateInterval * 4;
        } else {
            expires = modified + updateInterval * 2;
        }

        final ServiceStatus finalStatus;
        if (shutDownTriggered) {
            finalStatus = ServiceStatus.SHUTDOWN;
        } else if (overloadTriggered) {
            finalStatus = ServiceStatus.OVERLOAD;
        } else {
            finalStatus = nextStatus;
        }
        serviceStatusHeaders.set(new HttpHeaders() {
            {
                set("Service-Status", finalStatus.toString());
                set("Name", etfConfig.getProperty(EtfConfig.ETF_BRANDING_TEXT));
                set("Version", etfConfig.getVersion());
                setLastModified(modified);
                setExpires(expires);
            }
        });
        serviceStatus.set(new ExtendedServiceStatus(
                etfConfig.getProperty(EtfConfig.ETF_BRANDING_TEXT),
                finalStatus.toString(),
                modified,
                expires,
                etfConfig.getVersion(),
                allocatedMemory,
                presumableFreeMemory,
                tdDir.getTotalSpace(),
                tdDir.getFreeSpace(),
                String.format(Locale.ENGLISH, "%3f", mbean.getProcessCpuLoad()),
                statusWarningMessages));
    }

    @ApiOperation(value = "Get simple service status", notes = "Returns an empty response with status code 204, if the service is up and running."
            + " The service status is returned in the 'Service-Status' header. Please note that the service status can be accessed via the root path '/' to ensure "
            + "backward compatibility and also explicit with the API version prefix '/v2/heartbeat'", tags = {STATUS_TAG_NAME})
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Service is up and running", responseHeaders = {
                    @ResponseHeader(name = "Service-Status", description = STATUS_DESCRIPTION, response = ServiceStatus.class)
            }),
            @ApiResponse(code = 404, message = "Service is down"),
            @ApiResponse(code = 500, message = "Service is down"),
            @ApiResponse(code = 503, message = "Service is down")
    })
    @RequestMapping(value = {"/", "/v2/heartbeat"}, method = RequestMethod.HEAD)
    public ResponseEntity<Void> simpleHeartbeat() {
        return new ResponseEntity<>(serviceStatusHeaders.get(), HttpStatus.NO_CONTENT);
    }

    // @PreAuthorize("#oauth2.clientHasRole('ROLE_ADMIN')")
    @ApiOperation(value = "Get extended service status", notes = "Get service workload and health information.", produces = "application/json", tags = {
            STATUS_TAG_NAME})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Service is up and running"),
            @ApiResponse(code = 404, message = "Service is down"),
            @ApiResponse(code = 500, message = "Service is down"),
            @ApiResponse(code = 503, message = "Service is down")
    })
    @RequestMapping(value = "/v2/status", method = RequestMethod.GET, produces = "application/json")
    public @ResponseBody ExtendedServiceStatus getStatus() {
        return serviceStatus.get();
    }

    public void ensureStatusNotMajor() throws LocalizableApiError {
        if (ServiceStatus.valueOf(serviceStatus.get().status) == ServiceStatus.MAJOR) {
            List<String> messages = serviceStatus.get().messages;
            logger.error(messages.get(messages.size()-1));
            throw new LocalizableApiError("l.system.status.major", false, 503);
        }
    }

    public boolean inShutdownMode() {
        return this.shutDownTriggered;
    }

    public void triggerOverload() {
        if (!overloadTriggered) {
            logger.error("Fatal errors have been detected due to overloading of the system.");
            this.overloadTriggered = true;
        }
    }
}
