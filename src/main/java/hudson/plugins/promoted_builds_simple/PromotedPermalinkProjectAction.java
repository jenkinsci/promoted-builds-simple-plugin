/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.promoted_builds_simple;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author gcampb2
 */
public class PromotedPermalinkProjectAction implements PermalinkProjectAction {

    AbstractProject project;
    Map<String, List<Integer>> promotions;

    PromotedPermalinkProjectAction(AbstractProject target) {
        this.project = target;
        promotions = (Map) new HashMap<String, ArrayList<Integer>>();
    }

    public Run<?, ?> resolve(String level) {
        List<Integer> list = promotions.get(level);
        if (list != null) {
            Integer buildNumber = list.get(0);
            if (buildNumber != null) {
                return project.getBuildByNumber(buildNumber.intValue());
            }
        }
        return null;
    }

    public Map<String, List<Integer>> getPromotions() {
        return promotions;
    }

    private List<Integer> preRegister(String level, int buildNumber) {
        if (level != null) {
            List list = (List) promotions.get(level);
            if (list == null) {
                list = new ArrayList<Integer>();
                promotions.put(level, list);
            }
            return list;
        }
        return null;
    }

    public void registerPromotion(String level, int buildNumber) {
        unregisterRun(buildNumber);

        List list = preRegister(level, buildNumber);
        if (list != null) {
            if (list.isEmpty()) {
                list.add(buildNumber);
            } else if (!list.contains(buildNumber)) {
                for (int i = 0; i < list.size(); i++) {
                    if (((Integer) list.get(i)).compareTo(buildNumber) < 0) {
                        list.add(i, buildNumber);
                        break;
                    }
                }
            }
        }
    }

    protected void registerPromotionAtEnd(String level, int buildNumber) {
        /* used at startup when runs iterated in a largest-first fashion */
        List list = preRegister(level, buildNumber);
        if (list != null && !list.contains(buildNumber)) {
            list.add(buildNumber);
        }
    }

    public void unregisterRun(int buildNumber) {
        for (Entry<String, List<Integer>> entry : promotions.entrySet()) {
            Integer i = new Integer(buildNumber);
            List<Integer> list = entry.getValue();
            if (list.contains(i)) {
                entry.getValue().remove(i);
            }
        }
    }

    public List<Permalink> getPermalinks() {
        List<Permalink> r = new ArrayList<Permalink>();
        for (final Entry<String, List<Integer>> entry : promotions.entrySet()) {
            if (entry.getValue().size() > 0) {
                Permalink p = new Permalink() {

                    @Override
                    public String getDisplayName() {
                        return "Last " + entry.getKey();
                    }

                    @Override
                    public String getId() {
                        return entry.getKey();
                    }

                    @Override
                    public Run<?, ?> resolve(Job<?, ?> job) {
                        return PromotedPermalinkProjectAction.this.resolve(entry.getKey());
                    }
                };
                r.add(p);
            }
        }
        return r;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "";
    }

    public String getUrlName() {
        return null;
    }

    @Extension
    public static class PromotedPermalinkRunListener extends RunListener<Run> {

        public PromotedPermalinkRunListener() {
            super(Run.class);
        }

        @Override
        public void onDeleted(Run r) {
            Job j = r.getParent();
            PromotedPermalinkProjectAction permalinkAction = (PromotedPermalinkProjectAction) j.getAction(PromotedPermalinkProjectAction.class);
            permalinkAction.unregisterRun(r.number);
        }
    }

    @Extension
    public static final class ListenerImpl extends ItemListener {

        @Override
        public void onLoaded() {
            Iterator<Project> projects = Hudson.getInstance().getProjects().iterator();
            while (projects.hasNext()) {
                Project project = projects.next();
                PromotedPermalinkProjectAction permalinkAction = (PromotedPermalinkProjectAction) project.getAction(PromotedPermalinkProjectAction.class);
                Iterator<Run> itr = project.getBuilds().iterator();
                while (itr.hasNext()) {
                    Run run = itr.next();
                    PromoteAction promotion = run.getAction(PromoteAction.class);
                    if (promotion != null) {
                        if (permalinkAction == null) {
                            permalinkAction = new PromotedPermalinkProjectAction((AbstractProject) project);
                        }
                        permalinkAction.registerPromotionAtEnd(promotion.getLevel(), run.number);
                    }
                }
            }
        }
    }
}
