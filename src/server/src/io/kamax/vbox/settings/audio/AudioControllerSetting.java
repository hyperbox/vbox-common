/*
 * Hyperbox - Virtual Infrastructure Manager
 * Copyright (C) 2013 Maxime Dor
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

package io.kamax.vbox.settings.audio;

import io.kamax.hbox.constant.AudioController;
import io.kamax.hbox.constant.MachineAttribute;
import io.kamax.setting.StringSetting;

public class AudioControllerSetting extends StringSetting {

   public AudioControllerSetting(String value) {
      super(MachineAttribute.AudioController, value);
   }

   public AudioControllerSetting(AudioController value) {
      this(value.getId());
   }

}
