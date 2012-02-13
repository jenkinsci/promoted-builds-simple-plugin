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
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Run;
import hudson.util.RunList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author gcampb2
 */
public class Housekeeper extends JobProperty<Job<?, ?>> {

    private List<CleanupPolicy> policies;

    private Housekeeper(StaplerRequest req, JSONObject json)
            throws Descriptor.FormException, IOException {
        Object raw = json.get("policies");

        JSONArray array = null;
        if (raw instanceof JSONArray) {
            array = json.getJSONArray("policies");
        } else {
            array = new JSONArray();
            array.add((JSONObject) raw);
        }

        for (Object o : array) {
            JSONObject c = (JSONObject) o;
            addPolicy(new CleanupPolicy(c));
        }
    }

    public List<CleanupPolicy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<CleanupPolicy> policies) {
        this.policies = policies;
    }

    public final void addPolicy(CleanupPolicy policy) {
        if (this.policies == null) {
            this.policies = new ArrayList<CleanupPolicy>();
        }

        this.policies.add(policy);
    }

    public void clean(StaplerRequest req, PromotionLevel newPromotion)
            throws IOException {
        Job job = req.findAncestorObject(Job.class);
        Run run = req.findAncestorObject(Run.class);

        for (CleanupPolicy p : policies) {
            if (p.getTriggerLevel().getName().equals(newPromotion.getName())) {
                int skip = p.getCount();
                RunList rl = job.getBuilds();
                for (ListIterator itr = rl.listIterator(rl.indexOf(run)); itr.hasNext();) {
                    Run old = (Run) itr.next();
                    PromoteAction oldPromote = (PromoteAction) old.getAction(PromoteAction.class);
                    if (oldPromote != null && oldPromote.getLevel() != null) {
                        if (skip > 0 && oldPromote.getLevel().equals(newPromotion.getName())) {
                            skip--;
                        } else if (oldPromote.getLevel().equals(p.getTargetLevel().getName())) {
                            old.keepLog(false);
                        }
                    }
                }
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "Promoted Build cleanup";
        }

        @Override
        public Housekeeper newInstance(StaplerRequest req, JSONObject json) throws Descriptor.FormException {
            try {
                if (json.has("cleanupPromotions")) {
                    return new Housekeeper(req, json.getJSONObject("cleanupPromotions"));
                }
                return null;
            } catch (IOException e) {
                throw new FormException("Failed to create", e, null); // TODO:hmm
            }
        }
    }
}
