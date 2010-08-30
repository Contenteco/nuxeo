/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 */

package org.nuxeo.ecm.platform.queue.api;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Content that needs long processing. Asynchronous operation is started by the
 * queue when the content is handled. Content will remain in queue until it's
 * removed by an end of asynchronous operation call.
 *
 * @see QueueManager
 */
public final class QueueContent {

    public static final long DEFAULT_DELAY = 1000;

    public QueueContent(URI owner, String destination, String name) {
        this.owner = owner;
        this.destination = destination;
        this.name = name;
        delay = DEFAULT_DELAY;
    }

    final URI owner;

    final String destination;

    final String name;

    long delay;

    String comments;

    Serializable additionalInfo;

    public URI getResourceURI() throws URISyntaxException {
        return new URI("queueContent:" + destination + ":" + name);
    }

    /**
     * Gives the user who is performing the job.
     */
    public URI getOwner() {
        return owner;
    }

    /**
     * Gives the queue name on which the content should be handled
     */
    public String getDestination() {
        return destination;
    }

    /**
     * Uniquely names the content inside a queue.
     */
    public String getName() {
        return name;
    }

    /**
     * Gives the delay for locking purpose.
     */
    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    /**
     * Gives information about the task being processed.
     */
    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    /**
     * Additional info for any queue job having to re run the test.
     */
    public Serializable getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(Serializable additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

}
