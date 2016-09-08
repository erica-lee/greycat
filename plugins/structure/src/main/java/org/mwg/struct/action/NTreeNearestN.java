package org.mwg.struct.action;

import org.mwg.Callback;
import org.mwg.DeferCounter;
import org.mwg.Node;
import org.mwg.plugin.AbstractTaskAction;
import org.mwg.plugin.Job;
import org.mwg.struct.NTree;
import org.mwg.struct.tree.KDTree;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;
import org.mwg.task.TaskResultIterator;

public class NTreeNearestN extends AbstractTaskAction {

    public static String NAME = "nTreeNearestN";

    private final double[] _key;
    private final int _n;

    public NTreeNearestN(final double[] key, final int n) {
        super();
        this._key = key;
        this._n = n;
    }

    @Override
    public final void eval(final TaskContext context) {
        final TaskResult previousResult = context.result();
        final TaskResult nextResult = context.newResult();
        if (previousResult != null) {
            final DeferCounter defer = context.graph().newCounter(previousResult.size());
            final TaskResultIterator previousResultIt = previousResult.iterator();
            Object iter = previousResultIt.next();
            while (iter != null) {
                if (iter instanceof KDTree) {
                    ((NTree) iter).nearestN(_key, _n, new Callback<Node[]>() {
                        @Override
                        public void on(Node[] result) {
                            for (int i = 0; i < result.length; i++) {
                                nextResult.add(result[i]);
                            }
                            defer.count();
                        }
                    });
                } else {
                    defer.count();
                }
                iter = previousResultIt.next();
            }
            defer.then(new Job() {
                @Override
                public void run() {
                    context.continueWith(nextResult);
                }
            });
        } else {
            context.continueWith(nextResult);
        }
    }

    @Override
    public String toString() {
        return "nTreeNearestN(\'" + "\')";
    }

}