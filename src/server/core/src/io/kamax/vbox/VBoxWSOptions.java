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

import io.kamax.tool.AxStrings;
import io.kamax.tool.logging.Logger;
import java.net.URI;
import java.net.URISyntaxException;

public class VBoxWSOptions {

    private boolean hasOptions = false;
    private String protocol = "http";
    private String host = "localhost";
    private int port = 18083;
    private String username = "";
    private char[] passwd = new char[] {};

    public static boolean hasOptions(String options) {
        return !AxStrings.isEmpty(options);
    }

    public VBoxWSOptions(String options) throws URISyntaxException {
        if (!hasOptions(options)) {
            return;
        }

        Logger.debug("Given connect options: " + options);
        if (!options.contains("://")) {
            options = protocol + "://" + options;
        }
        Logger.debug("Adapted raw connect options: " + options);
        URI uri = new URI(options);

        protocol = uri.getScheme();
        host = uri.getHost();
        if (uri.getPort() > 0) {
            port = uri.getPort();
        }
        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            username = userInfo[0];
            if (userInfo.length == 2) {
                passwd = userInfo[1].toCharArray();
            }
        }

        hasOptions = true;
    }

    public boolean hasOptions() {
        return hasOptions;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }
    public char[] getPasswd() {
        return passwd;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String extractServer() {
        return protocol + "://" + host + ":" + port;
    }

}
