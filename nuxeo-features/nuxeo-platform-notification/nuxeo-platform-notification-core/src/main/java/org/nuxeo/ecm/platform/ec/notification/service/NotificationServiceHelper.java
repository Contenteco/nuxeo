/*
 * (C) Copyright 2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.ec.notification.service;

import org.nuxeo.ecm.platform.ec.placeful.interfaces.PlacefulService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @author <a href="mailto:npaslaru@nuxeo.com">Narcis Paslaru</a>
 *
 */
public final class NotificationServiceHelper {

    // Utility class.
    private NotificationServiceHelper() {
    }

    private static UserManager userManager;

    /**
     * Locates the notification service using NXRuntime.
     */
    public static NotificationService getNotificationService() {
        return (NotificationService) Framework.getRuntime().getComponent(
                NotificationService.NAME);
    }

    public static PlacefulService getPlacefulService() {
        return (PlacefulService) Framework.getRuntime().getComponent(
                PlacefulService.ID);
//        return Framework.getService(PlacefulService.class);
    }

    public static PlacefulService getPlacefulServiceBean() {
        return Framework.getService(PlacefulService.class);
    }

    public static UserManager getUsersService() {
        if (userManager == null) {
            userManager = Framework.getService(UserManager.class);
        }
        return userManager;
    }

}
