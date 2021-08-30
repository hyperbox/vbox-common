/*
 * Hyperbox - Virtual Infrastructure Manager
 * Copyright (C) 2021 Maxime Dor
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

package io.kamax.test.vbox;

import io.kamax.hbox.Configuration;
import io.kamax.hbox.exception.HypervisorException;
import io.kamax.tools.logging.LogLevel;
import io.kamax.tools.logging.Logger;
import io.kamax.vbox.VBoxWebSrv;
import io.kamax.vbox._VBoxWebSrv;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VBoxWebSrvTest {

    @BeforeClass
    public static void beforeClass() {
        Logger.setLevel(LogLevel.Debug);
    }

    @Test
    public void ok() {
        Configuration.setSetting("vbox.exec.web.path", "src/test/script/vboxwebsrv-ok");
        VBoxWebSrv srv = new VBoxWebSrv();
        srv.start();
        assertEquals(_VBoxWebSrv.State.Started, srv.getState());
        assertTrue(srv.isRunning());
        srv.stop();
        assertEquals(_VBoxWebSrv.State.Stopped, srv.getState());
    }

    @Test(expected = HypervisorException.class)
    public void portInUse() throws InterruptedException {
        Configuration.setSetting("vbox.exec.web.path", "src/test/script/vboxwebsrv-portAlreadyInUse");
        VBoxWebSrv srv = new VBoxWebSrv();
        srv.start();
        Thread.sleep(5000L);
    }

}
