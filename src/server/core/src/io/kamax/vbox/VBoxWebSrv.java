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

import io.kamax.hbox.exception.HyperboxException;
import io.kamax.net.NetUtil;
import io.kamax.tool.logging.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;


public class VBoxWebSrv implements _VBoxWebSrv {

    private String host = "localhost";
    private int port = 18083;
    private String authMethod = "null";
    private boolean sslUse = false;
    private String sslKeyFile = "";
    private String sslCaCert = "";

    private ProcessExecutor processExec;
    private StartedProcess processRun;

    public VBoxWebSrv() {

    }

    public VBoxWebSrv(String host, int port, String authMethod) {
        this.host = host;
        this.port = port;
        this.authMethod = authMethod;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            Logger.warning("VBox WebSrv Server is already running, ignoring call to start again");
        }

        Logger.debug("VBox WebSrv Server Start: Start");
        try {
            if (port == 0) {
                this.port = NetUtil.getRandomAvailablePort(host, port, 100);
                Logger.info("Using autodetected port: " + this.port);
            }

            List<String> args = new ArrayList<String>();
            args.add(VBoxPlatformUtil.getWebSrvCmd());
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

            processExec = new ProcessExecutor().command(args);
            processRun = processExec.start();
            for (int i = 0; i < 50 && isRunning() && NetUtil.isPortAvailable(host, port); i++) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    throw new HyperboxException("Interupted while waiting for WebServices to listen on port " + port, e);
                }
            }
            Logger.info("VirtualBox Web Services server started");
        } catch (IOException e) {
            throw new HyperboxException(e);
        } finally {
            Logger.debug("VBox WebSrv Server Start: End");
        }
    }

    @Override
    public synchronized void stop() {
        if (!isRunning()) {
            return;
        }

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

                Logger.info("Is VBox WS process stopped? " + !isRunning());
                Logger.info("VBox WS return code: " + process.exitValue());
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
            Logger.debug("VBox WebServices Server shutdown: End");
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return processRun != null && !processRun.getFuture().isDone();
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

}
