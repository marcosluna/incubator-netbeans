/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.modules.gradle.java.classpath;

import org.netbeans.modules.gradle.api.NbGradleProject;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.project.Project;
import org.netbeans.spi.java.classpath.ClassPathImplementation;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

/**
 *
 * @author Laszlo Kishalmi
 */
abstract class AbstractGradleClassPathImpl implements ClassPathImplementation {

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final PropertyChangeListener listener;
    private List<URL> rawResources;
    private List<PathResourceImplementation> resources;

    protected final Project project;

    protected AbstractGradleClassPathImpl(Project proj) {
        this.project = proj;
        final NbGradleProject watcher = proj.getLookup().lookup(NbGradleProject.class);
        listener = new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (watcher.isUnloadable()) {
                    return;
                }
                List<URL> newValue = createPath();
                boolean hasChanged;
                synchronized (AbstractGradleClassPathImpl.this) {
                    hasChanged = hasChanged(rawResources, newValue);
                    if (hasChanged) {
                        rawResources = newValue;
                        resources = null;
                    }
                }
                if (hasChanged) {
                    support.firePropertyChange(ClassPathImplementation.PROP_RESOURCES, null, null);
                }
            }
        };
        watcher.addPropertyChangeListener(WeakListeners.propertyChange(listener, null));
    }

    protected abstract List<URL> createPath();

    private boolean hasChanged(List<URL> oldValue, List<URL> newValue) {
        boolean ret = (oldValue == null) || (oldValue.size() != newValue.size());
        if (!ret) {
            assert oldValue != null;
            Iterator<URL> ol = oldValue.iterator();
            Iterator<URL> nl = newValue.iterator();
            while (!ret && ol.hasNext()) {
                ret = !ol.next().equals(nl.next());
            }
        }
        return ret;
    }

    @Override
    public final synchronized List<? extends PathResourceImplementation> getResources() {
        if (resources == null) {
            resources = new ArrayList<>();
            for (URL url : createPath()) {
                resources.add(ClassPathSupport.createResource(url));
            }
        }
        return resources;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        synchronized (support) {
            support.addPropertyChangeListener(propertyChangeListener);
        }
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        synchronized (support) {
            support.removePropertyChangeListener(propertyChangeListener);
        }
    }

    synchronized final void clearResourceCache() {
        resources = null;
        support.firePropertyChange(ClassPathImplementation.PROP_RESOURCES, null, null);
    }

    static void addAllFile(Collection<URL> ret, @NonNull Collection<File> files) {
        assert files != null;
        for (File f : files) {
            FileObject fo = FileUtil.toFileObject(f);
            if (fo != null) {
                if (FileUtil.isArchiveFile(fo)) {
                    ret.add(FileUtil.getArchiveRoot(fo).toURL());
                } else {
                    ret.add(fo.toURL());
                }
            } else {
                try {
                    URL url = Utilities.toURI(f).toURL();
                    String surl = url.toString();
                    if (surl.endsWith(".jar")) {             //NOI18N
                        url = new URL("jar:" + surl + "!/"); //NOI18N
                    } else if (!surl.endsWith("/")) {        //NOI18N
                        url = new URL(surl + "/");           //NOI18N
                    }
                    ret.add(url);
                } catch (MalformedURLException ex) {
                    //TODO: Shall not happen on files.
                }
            }
        }
    }
}
