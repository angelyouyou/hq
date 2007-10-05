/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.livedata.server.session;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hyperic.hq.livedata.LiveDataFormatter;
import org.hyperic.hq.livedata.shared.LiveDataCommand;

class FormatterRegistry {
    private static final FormatterRegistry INSTANCE = new FormatterRegistry();
    
    private List _formatters = new ArrayList();
    
    private FormatterRegistry() {}

    void registerFormatter(LiveDataFormatter f) {
        synchronized (_formatters) {
            _formatters.add(f);
        }
    }
    
    void unregisterFormatter(LiveDataFormatter f) {
        synchronized (_formatters) {
            _formatters.remove(f);
        }
    }

    /**
     * Find the formatters which can process the specified command 
     */
    Set findFormatters(LiveDataCommand cmd) {
        Set res = new HashSet();
        
        synchronized (_formatters) {
            for (Iterator i=_formatters.iterator(); i.hasNext(); ) {
                LiveDataFormatter f = (LiveDataFormatter)i.next();
                
                if (f.canFormat(cmd)) 
                    res.add(f);
            }
        }
        return res;
    }
    
    static FormatterRegistry getInstance() {
        return INSTANCE;
    }
}
