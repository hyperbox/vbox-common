/*
 * Hyperbox - Virtual Infrastructure Manager
 * Copyright (C) 2015 Maxime Dor
 *
 * http://kamax.io/hbox/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.vbox;

import io.kamax.hbox.Configuration;
import io.kamax.hbox.exception.HyperboxException;
import io.kamax.hbox.exception.HypervisorException;
import io.kamax.tools.logging.Logger;
import io.kamax.tools.net.NetUtil;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.listener.ProcessListener;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class VBoxWebSrv implements _VBoxWebSrv {

    private String host = "localhost";
    private int port = 18083;
    private String authMethod = "null";
    private boolean sslUse = false;
    private String sslKeyFile = "";
    private String sslCaCert = "";

    private final List<String> defaultExecPaths = new ArrayList<>();

    private ProcessExecutor processExec;
    private StartedProcess processRun;

    private State runState = State.Stopped;
    private String error;

    public VBoxWebSrv() {
        String execPath = Configuration.getSetting("vbox.exec.web.path");
        if (StringUtils.isNotBlank(execPath)) {
            defaultExecPaths.add(execPath);
        } else {
            defaultExecPaths.add("/usr/bin/vboxwebsrv");
            defaultExecPaths.add("/usr/lib/virtualbox/vboxwebsrv");
            defaultExecPaths.add("/usr/local/bin/vboxwebsrv");
            defaultExecPaths.add(VBoxPlatformUtil.getInstallPathWin() + "/VBoxWebSrv.exe");
        }
    }

    public VBoxWebSrv(String host, int port, String authMethod) {
        this();

        this.host = host;
        this.port = port;
        this.authMethod = authMethod;
    }

    private void validateExecutable(String path) {
        File exec = new File(path).getAbsoluteFile();
        if (!exec.exists()) {
            throw new HypervisorException(path + " does not exist");
        }

        if (!exec.isFile()) {
            throw new HypervisorException(path + " is not a file");
        }

        if (!exec.canExecute()) {
            throw new HypervisorException(path + " is not executable");
        }
    }

    private String locateExecutable() {
        for (String path : defaultExecPaths) {
            try {
                validateExecutable(path);
                Logger.debug(path + " is a valid VirtualBox WebService executable");
                return path;
            } catch (HyperboxException e) {
                Logger.debug("Not a valid web exec [" + path + "]: " + e.getMessage());
            }
        }

        throw new HypervisorException("Could not locate a valid VirtualBox WebService executable");
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            Logger.warning("VBox WebSrv Server is already running, ignoring call to start again");
        }

        Logger.debug("VBox WebSrv Server Start: Start");
        runState = State.Starting;
        try {
            if (port == 0) {
                this.port = NetUtil.getRandomAvailablePort(host, port, 100);
                Logger.info("Using autodetected port: " + this.port);
            }

            List<String> args = new ArrayList<>();
            args.add(locateExecutable());
            // Network settings
            args.add("-H"); // host
            args.add(host);
            args.add("-p"); // port
            args.add(Integer.toString(this.port));
            // Authentication settings
            args.add("-A"); // auth method
            args.add(authMethod);

            if (sslUse) {
                args.add("--ssl");
                args.add("--keyfile");
                args.add(sslKeyFile);
                args.add("--cacert");
                args.add(sslCaCert);
            }

            if (!NetUtil.isPortAvailable(host, port)) {
                throw new HypervisorException("Cannot start the WebService process: a process is already listening on " + host + ":" + port);
            }

            processExec = new ProcessExecutor().command(args).destroyOnExit()
                    .redirectOutput(new LogOutputStream() {
                        @Override
                        protected void processLine(String line) {
                            if (StringUtils.contains(line, "Socket connection successful: ")) {
                                runState = State.Started;
                            }

                            if (StringUtils.contains(line, "#### SOAP FAULT: Address already in use [detected]")) {
                                error = "WebService port " + port + " is already in use";
                                stop();
                            }
                        }
                    })
                    .addListener(new ProcessListener() {
                        @Override
                        public void afterStop(Process process) {
                            Logger.info("VirtualBox Web Service exec has exited with rc " + process.exitValue());
                            runState = State.Stopped;
                            synchronized (this) {
                                notifyAll();
                            }
                        }
                    });
            processRun = processExec.start();
            Runtime.getRuntime().addShutdownHook(new Thread(VBoxWebSrv.this::stop));

            Instant waitLimitTs = Instant.now().plusSeconds(5);
            while (Instant.now().isBefore(waitLimitTs)) {
                if (State.Started.equals(runState)) {
                    Logger.info("Started VBox WS Process");
                    return;
                }

                if (State.Stopped.equals(runState)) {
                    throw new HypervisorException(error);
                }

                if (State.Starting.equals(runState) && !isRunning()) {
                    throw new HypervisorException("Unexpected exit of the VirtualBox Web Service: " + getExitCode());
                }

                try {
                    wait(100L);
                } catch (InterruptedException e) {
                    // we don't care
                }
            }

            if (!NetUtil.isPortAvailable(host, port)) {
                Logger.warning("VirtualBox Web Services port is in use, but service was not detected as started. Assuming started");
                runState = State.Started;
                return;
            }

            stop();
            throw new HypervisorException("VirtualBox Web Services did not start within wait time");
        } catch (IOException e) {
            stop();
            throw new HypervisorException(e);
        } finally {
            Logger.debug("VBox WebSrv Server Start: End");
        }
    }

    @Override
    public synchronized void stop() {
        if (State.Stopped.equals(runState)) {
            return;
        }

        if (!isRunning()) {
            return;
        }

        runState = State.Stopping;
        Process process = processRun.getProcess();

        Logger.debug("VBox WebServices Server shutdown: Start");
        try {
            Logger.info("Stopping VBox WS Process");
            try {
                process.destroy();

                if (isRunning()) {
                    int j = 5;
                    for (int i = 1; i <= j && isRunning(); i++) {
                        Logger.info("Waiting for VBox WebServices process to stop (" + i + "/" + j + ")");
                        try {
                            processRun.getFuture().get(200L, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Logger.warning("Interrupted while waiting for VBox WS process");
                        } catch (ExecutionException e) {
                            Logger.warning("Error while waiting for VBox WS process to end", e);
                        } catch (TimeoutException e) {
                            // as expected
                        }
                    }
                }

                Logger.debug("Is VBox WS process stopped? " + !isRunning());
                Logger.debug("VBox WS return code: " + process.exitValue());
                Logger.info("Stopped VBox WS process");
            } catch (Throwable t) {
                if (isRunning()) {
                    processExec.destroyOnExit();
                    Logger.warning("Unable to stop VBox WS process, marked to destroy on exit", t);
                } else {
                    Logger.warning("VBox WS process stop was not clean", t);
                }
            }
        } finally {
            runState = State.Stopped;
            Logger.debug("VBox WebServices Server shutdown: End");
        }
    }

    @Override
    public synchronized boolean isRunning() {
        if (Objects.isNull(processRun)) {
            return false;
        }

        try {
            processRun.getProcess().exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    @Override
    public int getPort() {
        if (!isRunning()) {
            throw new IllegalStateException("VBox Web Server is not running");
        }

        return port;
    }

    @Override
    public void kill() {
        if (!isRunning()) {
            throw new IllegalStateException("VBox Web Server is not running");
        }

        processRun.getProcess().destroy();
    }

    @Override
    public int getExitCode() {
        if (processRun == null) {
            throw new IllegalStateException("VBox Web Server has not been started");
        }

        if (isRunning()) {
            throw new IllegalStateException("VBox Web Server has not been started");
        }

        return processRun.getProcess().exitValue();
    }

    @Override
    public State getState() {
        return runState;
    }

}
