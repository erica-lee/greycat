package org.mwg.internal.scheduler;

import org.mwg.Callback;
import org.mwg.Graph;
import org.mwg.GraphBuilder;
import org.mwg.task.TaskResult;

import static org.mwg.internal.task.CoreActions.print;
import static org.mwg.task.Tasks.newTask;

/**
 * @ignore ts
 */
public class TrampolineSchedulerTest {

    //  @Test
    public void test() {
        Graph g = new GraphBuilder().withScheduler(new TrampolineScheduler()).build();
        g.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean result) {
                newTask().loopPar("0", "99",
                        newTask().then(print("{{result}}"))
                ).execute(g, new Callback<TaskResult>() {
                    @Override
                    public void on(TaskResult result) {
                        System.out.println();
                    }
                });
            }
        });
    }

}