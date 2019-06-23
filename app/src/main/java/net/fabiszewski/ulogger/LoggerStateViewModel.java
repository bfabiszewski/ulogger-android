/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;import android.location.Location;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

class LoggerStateViewModel extends ViewModel {

    private MutableLiveData<LoggerState> state;

    MutableLiveData<LoggerState> getState() {
        if (state == null) {
            state = new MutableLiveData<>();
        }
        return state;
    }

    class LoggerState {
        final static int STATE_WAITING = 1;
        final static int STATE_FAILURE = 2;
        final static int STATE_SUCCESS = 3;

        final static int REASON_NONE = 0;
        final static int REASON_LOCATION_DISABLED = 1;
        final static int REASON_LOCATION_PERMISSION_DENIED = 2;

        Location location;
        int state = STATE_WAITING;
        int failureReason = REASON_NONE;
    }
}
