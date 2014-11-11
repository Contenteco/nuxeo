/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.webapp.navigation;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @author Florent BONNET
 *
 */

@XObject("treeManagerPlugin")
public class TreeManagerPluginExtension implements Serializable {

    private static final long serialVersionUID = 1L;

    @XNode("@filterClass")
    String filterClassName;

    @XNode("showFiles")
    Boolean showFiles;

    @XNode("showSection")
    Boolean showSection;

    @XNodeList(value = "excludedTypes/type", type = ArrayList.class, componentType = String.class)
    private List<String> excludedTypes;


    public TreeManagerPluginExtension() {
    }

    public TreeManagerPluginExtension(Boolean showFolders, Boolean showSection, String filterClassName) {
        showFiles = showFolders;
        this.showSection = showSection;
        this.filterClassName = filterClassName;
    }

    public Boolean getShowFiles() {
        return showFiles;
    }

    public void setShowFiles(Boolean showFiles) {
        this.showFiles = showFiles;
    }

    public Boolean getShowSection() {
        return showSection;
    }

    public void setShowSection(Boolean showSection) {
        this.showSection = showSection;
    }

    public List<String> getExcludedTypes() {
        return excludedTypes;
    }

    public void setExcludedTypes(List<String> excludedTypes) {
        this.excludedTypes = excludedTypes;
    }

    public String getFilterClassName() {
        return filterClassName;
    }

    public void setFilterClassName(String className) {
        filterClassName = className;
    }

}
