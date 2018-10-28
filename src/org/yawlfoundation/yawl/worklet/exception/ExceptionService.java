/*
 * Copyright (c) 2004-2012 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.worklet.exception;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.Marshaller;
import org.yawlfoundation.yawl.engine.interfce.TaskInformation;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.worklet.WorkletService;
import org.yawlfoundation.yawl.worklet.rdr.*;
import org.yawlfoundation.yawl.worklet.selection.WorkletRunner;
import org.yawlfoundation.yawl.worklet.support.EventLogger;
import org.yawlfoundation.yawl.worklet.support.WorkletSpecification;

import java.io.IOException;
import java.util.*;

import static org.yawlfoundation.yawl.worklet.rdr.RuleType.*;

/**
 *  The ExceptionService class manages the handling of exceptions that may occur
 *  during the life of a case instance. It receives events from the engine via
 *  InterfaceX at various milestones for constraint checking, and when certain
 *  exceptional events occur. It runs exception handlers when required, which may
 *  involve the running of compensatory worklets. It derives from the
 *  WorkletService class and uses many of the same methods as those used in the
 *  worklet selection process.
 *
 *  @author Michael Adams
 *  @version 0.8, 04-09/2006
 */

public class ExceptionService {

    private static Logger _log ;
    private ExceptionActions _actions;
    private final ExletRunnerCache _runners;
    private final WorkletService _wService;
    private final Object _mutex = new Object();


    public ExceptionService(WorkletService workletService) {
        super();
        _log = LogManager.getLogger(ExceptionService.class);
        _runners = new ExletRunnerCache();
        _wService = workletService;                 // register service with parent
    }


    public void completeInitialisation(boolean persisting) {
        _actions = new ExceptionActions(_wService.getEngineClient());
        if (persisting) restoreDataSets();             // reload running cases data
    }


    /*******************************/
    // INTERFACE X IMPLEMENTATIONS //
    /*******************************/

    /**
     * Handles a notification from the Engine that a case has been cancelled.
     * If the case passed has any exception handling worklets running for it,
     * they are also cancelled.
     *
     * @param caseID the case cancelled by the Engine
     */
    public void handleCaseCancellationEvent(String caseID) {

        synchronized (_mutex) {
            _log.info("CASE CANCELLATION EVENT");

            caseID = getIntegralID(caseID);      // strip back to basic caseID

            // cancel any/all running worklets for this case
            cancelWorkletsForCase(caseID);

            // if this case was a worklet running for another case, process
            // the worklet cancellation
            if (_runners.isCompensationWorklet(caseID)) {
                handleCompletingExceptionWorklet(caseID, null, true);
            }

            _runners.cancel(caseID);
        }
    }


    /**
     * Handles a notification from the Engine that a workitem is either starting
     * or has completed.
     * Checks the rules for the workitem, and evaluates any pre-constraints or
     * post-constraints (if any), and if a constraint has been violated, raises
     * the appropriate exception.
     *
     * @param wir the workitem that triggered the event
     * @param data the current case data (i.e. immediately prior to item start or
     *             after item completion)
     * @param preCheck true for pre-constraints, false for post-constraints
     */
    public void handleCheckWorkItemConstraintEvent(WorkItemRecord wir, String data,
                                                   boolean preCheck){
        synchronized (_mutex) {
            _log.info("CHECK WORKITEM CONSTRAINT EVENT");
            _log.info("Checking {}-constraints for workitem: {}",
                    preCheck ? "pre" : "post", wir.getID());

            checkConstraints(wir, augmentItemData(wir, data), preCheck);
        }
    }


    /**
     * Handles a notification from the Engine that a case is either starting
     * or has completed.
     * Checks the rules for the case and evaluates any pre-constraints or
     * post-constraints (if any), and if a constraint has been violated, raises
     * the appropriate exception.
     *
     * @param specID specification id of the case
     * @param caseID the id for the case
     * @param data the case-level data params
     * @param preCheck true for pre-constraints, false for post-constraints
     */
    public void handleCheckCaseConstraintEvent(YSpecificationID specID, String caseID,
                                               String data, boolean preCheck) {
        synchronized (_mutex) {

            _log.info("CHECK CASE CONSTRAINT EVENT");

            Element eData = JDOMUtil.stringToElement(data);
            if (preCheck) {
                _log.info("Checking constraints for start of case {} " +
                        "(of specification: {})", caseID, specID);

                checkConstraints(specID, caseID, eData, true);
            }
            else {                                                      // end of case
                _log.info("Checking constraints for end of case {}", caseID);
                checkConstraints(specID, caseID, eData, false);

                // treat this as a case complete event for exception worklets also
                if (_runners.isCompensationWorklet(caseID)) {
                    handleCompletingExceptionWorklet(caseID, eData, false);
                }
            }
        }
    }


    /**
     *  Handles a notification from the Engine that a workitem associated with the
     *  timeService has timed out.
     *  Checks the rules for timeout for the other items associated with this timeout item
     *  and raises thr appropriate exception.
     *
     * @param wir - the item that caused the timeout event
     * @param taskList - a list of taskids of those tasks that were running in
     *        parallel with the timeout task
     */
    public void handleTimeoutEvent(WorkItemRecord wir, String taskList) {

        synchronized (_mutex) {
            _log.info("TIMEOUT EVENT");
            if (taskList != null) {

                // split task list into individual taskids
                taskList = taskList.substring(1, taskList.lastIndexOf(']')); // remove [ ]

                // for each parallel task in the time out set
                for (String taskID : taskList.split(", ")) {

                    // get workitem record for this task & add it to the monitor's data
                    YSpecificationID specID = new YSpecificationID(wir);
                    List<WorkItemRecord> wirs =
                            getWorkItemRecordsForTaskInstance(specID, taskID);
                    if (wirs != null) {
                        handleTimeout(wirs.get(0));
                    }
                    else _log.info("No live work item found for task: {}", taskID);
                }
            }
            else handleTimeout(wir);
        }
    }


    private void handleTimeout(WorkItemRecord wir) {
        handleItemException(wir, wir.getDataListString(), RuleType.ItemTimeout);
    }

    public void handleResourceUnavailableException(String resourceID, WorkItemRecord wir, 
                                                   String caseData, boolean primary) {
        handleItemException(wir, caseData, ItemResourceUnavailable);
    }


    public String handleWorkItemAbortException(WorkItemRecord wir, String data) {
        return handleItemException(wir, data, RuleType.ItemAbort);
    }


    public String handleConstraintViolationException(WorkItemRecord wir, String data) {
        return handleItemException(wir, data, ItemConstraintViolation);
    }


    private String handleItemException(WorkItemRecord wir, String dataStr,
                                       RuleType ruleType) {
        String msg;
        YSpecificationID specID = new YSpecificationID(wir);
        Element data = augmentItemData(wir, dataStr);

        // get the exception handler for this task (if any)
        RdrPair pair = getExceptionHandler(specID, wir.getTaskID(), data, ruleType);

        // if pair is null there's no rules defined for this type of constraint
        if (pair == null) {
            msg = "No " + ruleType.toLongString() + " rules defined for workitem: " +
                    wir.getTaskID();
        }
        else {
            if (! pair.hasNullConclusion()) {                // we have a handler
                msg = ruleType.toLongString() + " exception raised for work item: " +
                        wir.getID();
                raiseException(wir, pair, data, ruleType);
            }
            else {                      // there are rules but the item passes
                msg = "Workitem '" + wir.getID() + "' has passed " +
                        ruleType.toLongString() + " rules.";
            }
        }
        _log.info(msg);
        return StringUtil.wrap(msg, "result");
    }

    //***************************************************************************//

    /**
     * Checks for case-level constraint violations.
     *
     * @param pre true for pre-constraints, false for post-constraints
     */
    private void checkConstraints(YSpecificationID specID, String caseID,
                                  Element data, boolean pre) {
        String sType = pre ? "pre" : "post";
        RuleType xType = pre ? RuleType.CasePreconstraint : RuleType.CasePostconstraint;

        // get the exception handler that would result from a constraint violation
        RdrPair pair = getExceptionHandler(specID, null, data, xType) ;

        // if pair is null there's no rules defined for this type of constraint
        if (pair == null) {
            _log.info("No {}-case constraints defined for spec: {}", sType, specID);
        }
        else {
            if (! pair.hasNullConclusion()) {                // there's been a violation
                _log.info("Case {} failed {}-case constraints", caseID, sType);
                raiseException(caseID, pair, data, xType) ;
            }
            else {                                // there are rules but the case passes
                _wService.getServer().announceConstraintPass(caseID, data, xType);
                _log.info("Case {} passed {}-case constraints", caseID, sType);
            }
        }
    }


    /**
     * Checks for item-level constraint violations.
     *
     * @param wir the WorkItemRecord for the workitem
     * @param pre true for pre-constraints, false for post-constraints
     */
    private void checkConstraints(WorkItemRecord wir, Element data, boolean pre) {
        String itemID = wir.getID();
        String taskID = wir.getTaskID();
        YSpecificationID specID = new YSpecificationID(wir);
        String sType = pre? "pre" : "post";
        RuleType xType = pre? RuleType.ItemPreconstraint : RuleType.ItemPostconstraint;

        // get the exception handler that would result from a constraint violation
        RdrPair pair = getExceptionHandler(specID, taskID, data, xType) ;

        // if pair is null there's no rules defined for this type of constraint
        if (pair == null) {
            _log.info("No {}-task constraints defined for task: {}", sType, taskID);
            if (pre) _wService.getEventQueue().notifyExceptionHandlingCompleted(wir);
        }
        else {
            if (! pair.hasNullConclusion()) {                // there's been a violation
                _log.info("Workitem {} failed {}-task constraints", itemID, sType);
                raiseException(wir, pair, data, xType) ;
            }
            else {                                // there are rules but the case passes
                _wService.getServer().announceConstraintPass(wir, data, xType);
                _log.info("Workitem {} passed {}-task constraints", itemID, sType);
                if (pre) _wService.getEventQueue().notifyExceptionHandlingCompleted(wir);
            }
        }
    }


    /**
     * Discovers whether this case or item has rules for this exception type, and if so,
     * returns the result of the rule evaluation. Note that if the conclusion
     * returned from the search is empty, no exception has occurred.
     *
     * @param taskID item's task id, or null for case-level exception
     * @param xType the type of exception triggered
     * @return an RdrConclusion representing an exception handling process,
     *         or null if no rules are defined for these criteria
     */
    private RdrPair getExceptionHandler(YSpecificationID specID, String taskID,
                                        Element data, RuleType xType) {
        return _wService.getRdrEvaluator().evaluate(specID, taskID, data, xType);
    }


    /**
     * Raises a case-level exception by creating a ExletRunner for the exception
     * process, then starting the processing of it
     * @param pair represents the exception handling process
     * @param xType the int descriptor of the exception type (WorkletService xType)
     */
    private void raiseException(String caseID, RdrPair pair,
                                Element data, RuleType xType) {
        _log.debug("Invoking exception handling process for case: {}", caseID);
        _wService.getServer().announceException(caseID, data, pair.getLastTrueNode(), xType);
        ExletRunner runner = new ExletRunner(caseID, pair.getConclusion(), xType);
        runner.setRuleNodeID(pair.getLastTrueNode().getNodeId());
        runner.setData(data);
        processException(runner);
    }


    /**
     * Raises an item-level exception - see above for more info
     * @param pair represents the exception handling process
     * @param wir the WorkItemRecord of the item that triggered the event
     * @param xType the int descriptor of the exception type (WorkletService xType)
     */
    private void raiseException(WorkItemRecord wir, RdrPair pair, Element data,
                                RuleType xType){
        _log.debug("Invoking exception handling process for item: {}", wir.getID());
        _wService.getServer().announceException(wir, data, pair.getLastTrueNode(), xType);
        ExletRunner runner = new ExletRunner(wir, pair.getConclusion(), xType);
        runner.setRuleNodeID(pair.getLastTrueNode().getNodeId());
        runner.setData(data);
        processException(runner);
    }


    /**
     * Raises an external exception - see above for more info
     * @param pair represents the exception handling process
     */
    private void raiseException(ExletRunner runner, RdrPair pair, Element data) {
        _log.debug("Invoking exception handling process for external: {}", runner.getCaseID());
        _wService.getServer().announceException(runner.getWir(), data, pair.getLastTrueNode(),
                runner.getRuleType());
        runner.setRuleNodeID(pair.getLastTrueNode().getNodeId());
        processException(runner);
    }


    /**
     * Begin (or continue after a worklet completes) the exception handling process
     *
     * @param runner the HandlerRunner for this handler process
     */
    private void processException(ExletRunner runner) {
        _runners.add(runner);
        boolean doNextStep = true;

        while (runner.hasNextAction() && doNextStep) {
            doNextStep = doAction(runner);           // becomes false if worklets started
            runner.incActionIndex();                 // move to next primitive
        }

        // if no more actions to do (or worklets) in runner, remove it
        if (! (runner.hasNextAction() || runner.hasRunningWorklet())) {
            _runners.remove(runner);
            if (runner.getRuleType() == RuleType.ItemPreconstraint) {
                _wService.getEventQueue().notifyExceptionHandlingCompleted(runner.getWir());
            }
        }
    }


    /**
     * Perform a single step in an exception handing process
     * @param runner the HandlerRunner for this exception handling process
     * @return true if ok for processing to continue
     */
    private boolean doAction(ExletRunner runner) {
        String action = runner.getNextAction();
        String target = runner.getNextTarget();
        boolean success = true;

        _log.info("Exception process step {}. Action = {}, Target = {}",
                runner.getActionIndex(), action, target);

        // short circuit if action doesn't apply to target
        if (! target.equals("workitem") && ExletAction.fromString(action).isItemOnlyAction()) {
            _log.error("Unexpected target type '{}' for action '{}'. Step ignored",
                    target, action) ;
            return true;
        }

        switch (ExletAction.fromString(action)) {
            case Continue : doContinue(runner); break;                    // un-suspend
            case Suspend  : doSuspend(runner); break;
            case Remove   : doRemove(runner); break;                      // cancel
            case Restart  : restartWorkItem(runner.getWir()); break;
            case Complete : forceCompleteWorkItem(runner.getWir(),
                                    runner.getWorkItemUpdatedData()); break;
            case Fail     : failWorkItem(runner.getWir()); break;
            case Compensate:

                // launch & run compensatory worklet(s)
                // if launch succeeds, break out of loop while worklet runs
                success = ! doCompensate(runner, target);
                break;

            case Rollback: {
                _log.warn("Rollback is not yet implemented - will ignore this step.");
                break;
            }
            default:  {
                _log.warn("Unrecognised action type '{}' in exception handling primitive" +
                          " - will ignore this primitive.", action);
            }
        }
        return success;                   // successful processing of exception primitive
    }



    /**
     * Calls the appropriate continue method for the exception target scope
     *
     * @param runner the HandlerRunner stepping through this exception process
     */
    private void doContinue(ExletRunner runner) {
        String target = runner.getNextTarget();
        switch (ExletTarget.fromString(target)) {
            case Workitem : {
                WorkItemRecord wir = _actions.unsuspendWorkItem(runner.getWir());
                runner.setItem(wir);     // refresh item
                runner.unsetItemSuspended();
                break;
            }
            case Case:
            case AllCases:
            case AncestorCases: {
                _actions.unsuspendList(runner);
                runner.clearCaseSuspended();
                break;
            }
            default: _log.error("Unexpected target type '{}' " +
                    "for exception handling primitive 'continue'", target) ;
        }
    }


    /**
     * Calls the appropriate suspend method for the exception target scope
     *
     * @param runner the HandlerRunner stepping through this exception process
     */
    private void doSuspend(ExletRunner runner) {
        String target = runner.getNextTarget();
        switch (ExletTarget.fromString(target)) {
            case Workitem      : suspendWorkItem(runner); break;
            case Case          : _actions.suspendCase(runner); break;
            case AncestorCases : _actions.suspendAncestorCases(_wService.getAllRunners(),
                                                               runner);
                                 break;
            case AllCases      : _actions.suspendAllCases(runner); break;
            default: _log.error("Unexpected target type '{}' " +
                          "for exception handling primitive 'suspend'", target) ;
        }
    }


    /**
     * Calls the appropriate remove method for the exception target scope
     *
     * @param runner the HandlerRunner stepping through this exception process
     */
    private void doRemove(ExletRunner runner) {
        String target = runner.getNextTarget();
        switch (ExletTarget.fromString(target)) {
            case Workitem      : removeWorkItem(runner.getWir()); break;
            case Case          : removeCase(runner); break;
            case AncestorCases : removeAncestorCases(runner); break;
            case AllCases      : removeAllCases(runner); break;
            default: _log.error("Unexpected target type '{}' " +
                          "for exception handling primitive 'remove'", target) ;
        }
    }


    private boolean doCompensate(ExletRunner runner, String target) {
        Set<WorkletSpecification> workletList = _wService.getLoader().parseTarget(target);
        Set<WorkletRunner> worklets = _wService.getEngineClient()
                .launchWorkletList(runner.getWir(), runner.getDataForCaseLaunch(),
                        workletList, runner.getRuleType());
        if (!worklets.isEmpty()) {
            for (WorkletRunner worklet : worklets) {
                worklet.setParentCaseID(runner.getCaseID());
                worklet.setRuleNodeID(runner.getRuleNodeID());
                if (runner.getRuleType().isCaseLevelType()) {
                    worklet.setParentSpecID(getSpecIDForCaseID(runner.getCaseID()));
                }
                worklet.logLaunchEvent(worklet.getParentSpecID(), null);
            }
            runner.addWorkletRunners(worklets);
        }
        else _log.error("Unable to load compensatory worklet(s), will ignore: {}",
            target);
        return !worklets.isEmpty();
    }


    /**
     * Deals with the end of an exception worklet case.
     *  @param caseId - the id of the completing case
     *  @param wlCasedata - the completing case's datalist Element
     *  @param cancelled - true if the worklet has been cancelled, false for a normal
     *  completion
     */
    private void handleCompletingExceptionWorklet(String caseId, Element wlCasedata,
                                                  boolean cancelled) {

        // get and remove the HandlerRunner that launched this worklet
        ExletRunner runner = _runners.getRunnerForWorklet(caseId);
        _log.debug("Worklet ran as exception handler for case: {}", runner.getCaseID());

        if (! cancelled) {
            updateData(runner, wlCasedata);
        }

        WorkletRunner worklet = runner.removeWorklet(caseId);

        // log the worklet's case completion event
        String event = cancelled ? EventLogger.eCancel : EventLogger.eComplete;
        EventLogger.log(event, caseId, worklet.getParentSpecID(), "",
                runner.getCaseID(), -1) ;

         //if all worklets have completed, process the next exception primitive
         if (! runner.hasRunningWorklet()) {
            _log.info("All compensatory worklets have finished execution - " +
                    "continuing exception processing.");
            processException(runner);
        }
    }


    /* Updates parent case or work item on completion of a worklet case.
     *
     * Update data of parent workitem/case if allowed and required and not cancelled
     * ASSUMPTION: the output data of the worklet will be used to update the
     * case/item only if:
     *   1. it is a case level exception and the case has been suspended, in
     *      which case the case-level data is updated; or
     *   2. it is an pre-executing item level exception and the item is suspended,
     *      in which case the case-level data is updated (because the item has not
     *      yet received data values from the case before starting); or
     *   3. it is an executing item exception and the item is suspended, in which
     *      case the item-level data is updated
     */
    private void updateData(ExletRunner runner, Element wlCasedata) {
        if (runner.isItemSuspended() && runner.getRuleType().isExecutingItemType()) {
            updateItemData(runner, wlCasedata);
        }
        else if (runner.isCaseSuspended() || runner.isItemSuspended()) {
            updateCaseData(runner, wlCasedata);
        }
    }


     /**
     * Suspends the specified workitem
     * @param hr the HandlerRunner instance with the workitem to suspend
     */
    private boolean suspendWorkItem(ExletRunner hr) {
        WorkItemRecord wir = hr.getWir();
        if (_actions.suspendWorkItem(wir)) {
            hr.setItemSuspended();                  // record the action
            hr.setItem(updateWIR(wir));             // refresh the stored wir
            Set<WorkItemRecord> wirSet = new HashSet<WorkItemRecord>();
            wirSet.add(hr.getWir());          // ... and the list
            hr.setSuspendedItems(wirSet);          // record the suspended item
            return true ;
        }
        return false ;
    }


    public boolean suspendWorkItem(String itemID) {
        return _actions.suspendWorkItem(getWorkItemRecord(itemID));
    }


    public Set<WorkItemRecord> suspendCase(String caseID) {
        return _actions.suspendCase(caseID);
    }


    /**
     * Cancels the workitem specified
     * @param wir the workitem (record) to cancel
     */
    private void removeWorkItem(WorkItemRecord wir) {
        try {
            // only executing items can be removed, so if its only fired or enabled, or
            // if its suspended, move it to executing first
            wir = moveToExecuting(wir);

            if (wir.getStatus().equals(WorkItemRecord.statusExecuting)) {
                String msg = _wService.getEngineClient().cancelWorkItem(wir.getID());
                if (_wService.successful(msg)) {
                    _log.info("WorkItem successfully removed from Engine: {}", wir.getID());
                }
                else _log.error("Failed to remove work item: {}", msg);
            }
            else _log.error("Can't remove a workitem with a status of {}", wir.getStatus());

        }
        catch (IOException ioe) {
            _log.error("Failed to remove workitem '{}': {}", wir.getID(), ioe.getMessage());
        }
    }


    /**
     * Cancels the specified case
     * @param hr the HandlerRunner instance with the case to cancel
     */
    private void removeCase(ExletRunner hr) {
        removeCase(hr.getCaseID());
    }


    /**
     * Cancels the specified case
     * @param caseID the id of the case to cancel
     */
    private void removeCase(String caseID) {
        try {
            if (_wService.successful(_wService.getEngineClient().cancelCase(caseID))) {
                _log.info("Case successfully removed from Engine: {}", caseID);
            }
        }
        catch (IOException ioe) {
            _log.error("Failed to remove case '{}': {}", caseID, ioe.getMessage());
        }
    }


    /**
     * Cancels all running instances of the specification passed
     */
    private void removeAllCases(ExletRunner runner) {
        try {
            YSpecificationID specID = getSpecIDForCaseID(runner.getCaseID());
            String casesForSpec = _wService.getEngineClient().getCases(specID);
            Element eCases = JDOMUtil.stringToElement(casesForSpec);

            for (Element eCase : eCases.getChildren()) {
                removeCase(eCase.getText());
            }
        }
        catch (IOException ioe) {
            _log.error("Failed to remove all cases for specification: {}",
                    ioe.getMessage());
        }
    }


    /**
     * Cancels all running worklet cases in the hierarchy of handlers
     * @param runner - the runner for the child worklet case
     */
    private void removeAncestorCases(ExletRunner runner) {
        String caseID = getFirstAncestorCase(runner);

        _log.info("The ultimate parent case of this worklet has an id of: {}", caseID);
        _log.info("Removing all child compensatory worklets of case: {}", caseID);

        cancelWorkletsForCase(caseID);
        removeCase(caseID);                                // remove ultimate parent
    }


    /** returns the ultimate ancestor case of the runner passed */
    private String getFirstAncestorCase(ExletRunner runner) {
        if (runner == null) return null;

        String ultimateID = runner.getCaseID();         // i.e. id of parent case
        Map<String, WorkletRunner> runnerMap = _wService.getAllRunners();
        WorkletRunner wRunner = runnerMap.get(ultimateID);
        while (wRunner != null) {                       // if parent case is a worklet
            ultimateID = wRunner.getParentCaseID();     // get it's id
            wRunner = runnerMap.get(ultimateID);
        }

        return ultimateID ;
    }


    /**
     * ForceCompletes the specified workitem
     * @param wir the item to ForceComplete
     * @param out the final data params for the workitem
     */
    private void forceCompleteWorkItem(WorkItemRecord wir, Element out) {

        // only executing items can complete, so if its only fired or enabled, or
        // if its suspended, move it to executing first
        wir = moveToExecuting(wir);

        if (wir.getStatus().equals(WorkItemRecord.statusExecuting)) {
            try {
                Element data = mergeCompletionData(wir, wir.getDataList(), out);
                String msg = _wService.getEngineClient().forceCompleteWorkItem(wir, data);
                if (_wService.successful(msg)) {
                    _log.info("Item successfully force completed: {}", wir.getID());
                }
                else _log.error("Failed to force complete workitem: {}", msg);
            }
            catch (IOException ioe) {
                _log.error("Failed to force complete workitem: " + wir.getID(), ioe);
            }
        }
        else _log.error("Can't force complete a workitem with a status of {}", wir.getStatus());
    }


    /** restarts the specified workitem */
    private void restartWorkItem(WorkItemRecord wir) {

        // ASSUMPTION: Only an 'executing' workitem may be restarted
        if (wir.getStatus().equals(WorkItemRecord.statusExecuting)) {
            try {
                String msg = _wService.getEngineClient().restartWorkItem(wir.getID());
                if (_wService.successful(msg)) {
                    _log.info("Item successfully restarted: {}", wir.getID());
                }
                else _log.error("Failed to restart workitem: {}", msg);
            }
            catch (IOException ioe) {
                _log.error("Exception attempting restart workitem: " + wir.getID(), ioe);
            }
        }
        else _log.error("Can't restart a workitem with a status of {}", wir.getStatus());
    }


    /** Cancels a workitem and marks it as failed */
    private void failWorkItem(WorkItemRecord wir) {
        try {
            // only executing items can be failed, so if its only fired or enabled, or
            // if its suspended, move it to executing first
            wir = moveToExecuting(wir);

            // ASSUMPTION: Only an 'executing' workitem may be failed
            if (wir.getStatus().equals(WorkItemRecord.statusExecuting)) {
                String result = _wService.getEngineClient().failWorkItem(wir);
                if (_wService.successful(result)) {
                    _log.info("WorkItem successfully failed: {}", wir.getID());
                    if (result.contains("cancelled")) {
                        _log.info("Case {} was unable to continue as a consequence of the " +
                                "workItem force fail, and was also cancelled.", wir.getRootCaseID());
                    }
                }
                else _log.error(StringUtil.unwrap(result));
            }
            else _log.error("Can't fail a workitem with a status of {}", wir.getStatus());
        }
        catch (IOException ioe) {
            _log.error("Exception attempting to fail workitem: " + wir.getID(), ioe);
        }
    }


    /**
     * Refreshes a locally cached WorkItemRecord with the Engine stored one
     * @param wir the item to refresh
     * @return the refreshed workitem, or the unchanged workitem on exception
     */
    private WorkItemRecord updateWIR(WorkItemRecord wir) {
        try {
            wir = _wService.getEngineStoredWorkItem(wir.getID(),
                    _wService.getEngineClient().getSessionHandle());
        }
        catch (IOException ioe){
            _log.error("IO Exception attempting to update WIR: " + wir.getID(), ioe);
        }
        return wir ;
    }


    /**
     * Updates a workitem's data param values with the output data of a
     * completing worklet, then copies the updates to the engine stored workitem
     * @param runner the HandlerRunner containing the exception handling process
     * @param wlData the worklet's output data params
     */
    private void updateItemData(ExletRunner runner, Element wlData) {

        // update the items datalist with corresponding values from the worklet
        Element out = _wService.updateDataList(runner.getWorkItemDatalist(), wlData) ;

        // copy the updated list to the (locally cached) wir
        runner.getWir().setDataList(out);

        // and copy that back to the engine
        try {
            _wService.getEngineClient().updateWorkItemData(runner.getWir(), out);
        }
        catch (IOException ioe) {
            _log.error("IO Exception calling interface X");
        }
    }


    /**
     * Updates the case-level data params with the output data of a
     * completing worklet, then copies the updates to the engine stored caseData
     * @param runner the HandlerRunner containing the exception handling process
     * @param wlData the worklet's output data params
     */
    private void updateCaseData(ExletRunner runner, Element wlData) {
        try {

            // get engine copy of case data
            Element in = getCaseData(runner.getCaseID());

            // update data values as required
            Element updated = _wService.updateDataList(in, wlData);

            // and copy that back to the engine
            _wService.getEngineClient().updateCaseData(runner.getCaseID(), updated);
        }
        catch (IOException ioe) {
            _log.error("IO Exception calling interface X");
        }
    }


    // merge the input and output data together
    private Element mergeCompletionData(WorkItemRecord wir, Element in, Element out)
            throws IOException {
        if (out == null) out = new Element(in.getName());
        String mergedOutputData = Marshaller.getMergedOutputData(in, out);
        if (StringUtil.isNullOrEmpty(mergedOutputData)) {
            if (_log.isWarnEnabled()) {
                _log.warn("Problem merging workitem data: In [{}] Out [{}]",
                        JDOMUtil.elementToStringDump(in),
                        JDOMUtil.elementToStringDump(out));
            }
        }
        YSpecificationID specID = new YSpecificationID(wir);
        TaskInformation taskInfo = _wService.getTaskInformation(specID, wir.getTaskID(),
                _wService.getEngineClient().getSessionHandle());
        List<YParameter> outputParams = taskInfo.getParamSchema().getOutputParams();
        try {
            return JDOMUtil.stringToElement(
                    Marshaller.filterDataAgainstOutputParams(mergedOutputData, outputParams));
        }
        catch (JDOMException jde) {
            return (in != null) ? in : out;
        }
    }


    /** cancels all worklets running as exception handlers for a case when that
     *  parent case is cancelled
     */
    private void cancelWorkletsForCase(String caseID) {
        boolean hasRunningWorklet = false;         // flag for msg if no runners

        // iterate through all the live runners for this case
        for (ExletRunner runner : _runners.getRunnersForCase(caseID)) {
            hasRunningWorklet = cancelWorkletsForRunner(runner) || hasRunningWorklet;
        }
        if (! hasRunningWorklet) {
            _log.info("No compensatory worklets running for cancelled case: {}", caseID);
        }
    }


    private boolean cancelWorkletsForRunner(ExletRunner runner) {

        // only want runners with live worklet(s)
        if (! runner.hasRunningWorklet()) return false;

        // cancel each worklet case of this runner
        for (WorkletRunner worklet : runner.getWorkletRunners()) {

            // recursively call this method for each child worklet (in case
            // they also have worklets running)
            String workletCaseID = worklet.getCaseID();
            cancelWorkletsForCase(workletCaseID);

            _log.info("Worklet case running for the cancelled parent case " +
                    "has id of: {}", workletCaseID);

            EventLogger.log(EventLogger.eCancel, workletCaseID,
                    worklet.getWorkletSpecID(), "", runner.getCaseID(), -1);

            removeCase(workletCaseID);
            runner.removeWorklet(worklet);
        }
        return true;
    }


    private WorkItemRecord moveToExecuting(WorkItemRecord wir) {
        if (wir.getStatus().equals(WorkItemRecord.statusSuspended)) {
            _actions.unsuspendWorkItem(wir);
        }
        if (wir.getStatus().equals(WorkItemRecord.statusFired)) {
            wir = _wService.getEngineClient().checkOutFiredWorkItem(wir);
        }
        else if (wir.getStatus().equals(WorkItemRecord.statusEnabled)) {
            Set<WorkItemRecord> cos = _wService.getEngineClient().checkOutItem(wir);
            if (! cos.isEmpty()) {
                wir = cos.iterator().next();
            }
        }
        return wir;
    }


    public Set<WorkletRunner> getRunningWorklets() {
        return _runners.getAllWorklets();
    }


    public List<String> getExternalTriggersForItem(String itemID) {
        return _wService.getRdrEvaluator().getExternalTriggersForItem(itemID);
    }


    public void cleanseWorkletRunners(Set<WorkletRunner> toRemove) {
        _runners.removeWorkletRunners(toRemove);
    }


    /**
     * Retrieves a list of all workitems that are instances of the specified task
     * within the specified spec
     * @param specID
     * @param taskID
     * @return the list of workitems
     */
    private List<WorkItemRecord> getWorkItemRecordsForTaskInstance(
            YSpecificationID specID, String taskID) {

        // get all the live work items that are instances of this task
        List<WorkItemRecord> items = _wService.getEngineClient()
                .getLiveWorkItemsForIdentifier("task", taskID) ;
        List<WorkItemRecord> toRemove = new ArrayList<WorkItemRecord>();

        if (items != null) {

            // filter those for this spec
            for (WorkItemRecord wir : items) {
                YSpecificationID wirSpecID = new YSpecificationID(wir);
                if (! wirSpecID.equals(specID)) toRemove.add(wir);
            }
            items.removeAll(toRemove);
        }
        return items;
    }


    /**
     * Strips off the non-integral part of a case id
     * @param id the case id to fix
     * @return the integral part of the caseid passed
     */
    private String getIntegralID(String id) {
        int end = id.indexOf('.') ;         // where's the decimal point
        if (end == -1) return id ;          // no dec point, no change required
        return id.substring(0, end);        // else return its substring
    }


    /** returns the specified wir for the id passed */
    public WorkItemRecord getWorkItemRecord(String itemID) {
        try {
            return _wService.getEngineClient().getEngineStoredWorkItem(itemID);
        }
        catch (IOException ioe) {
            _log.error("Failed to get WIR '{}' from engine: {}", itemID, ioe.getMessage());
            return null ;
        }
    }


    /** returns the spec id for the specified case id */
    public YSpecificationID getSpecIDForCaseID(String caseID) {
        return _wService.getEngineClient().getSpecificationIDForCase(caseID);
    }


    /** retrieves a complete list of external exception triggers from the ruleset
     *  for the specified case
     * @param caseID - the id of the case to get the triggers for
     * @return the (String) list of triggers
     */
    public List getExternalTriggersForCase(String caseID) {
        YSpecificationID specID = getSpecIDForCaseID(caseID);
        if (specID != null) {
            RdrTree tree = _wService.getRdrEvaluator().getTree(specID, null,
                    CaseExternalTrigger);
            return _wService.getRdrEvaluator().getExternalTriggers(tree) ;
        }
        return null;
    }


    /**
     * Raise an externally triggered exception
     * @param level - the level of the exception (case/item)
     * @param id - the id of the case or item on which the exception is being raised
     * @param trigger - the identifier of (or reason for) the external exception
     */
    public void raiseExternalException(String level, String id, String trigger) {

        synchronized (_mutex) {
            _log.info("EXTERNAL EXCEPTION EVENT");        // note to log

            String caseID, taskID;
            YSpecificationID specID;
            WorkItemRecord wir = null;
            RuleType xType ;

            if (level.equalsIgnoreCase("case")) {                // if case level
                caseID = id;
                specID = getSpecIDForCaseID(caseID);
                taskID = null;
                xType = CaseExternalTrigger;
            }
            else {                                               // else item level
                wir = getWorkItemRecord(id);
                caseID = wir.getRootCaseID();
                specID = new YSpecificationID(wir);
                taskID = wir.getTaskID();
                xType = ItemExternalTrigger;
            }

            // add trigger value to case data
            Element eData = augmentExternalData(getCaseData(caseID), trigger);

            // get the exception handler for this trigger
            RdrPair pair = getExceptionHandler(specID, taskID, eData, xType);

            // if pair is null there's no rules defined for this type of constraint
            if (pair == null) {
                _log.error("No external exception rules defined for spec: {}" +
                        ". Unable to raise exception for '{}'", specID, trigger);
            }
            else {
                ExletRunner runner = (wir == null) ?
                    new ExletRunner(caseID, pair.getConclusion(), xType) :
                    new ExletRunner(wir, pair.getConclusion(), xType);
                runner.setTrigger(trigger);
                runner.setData(eData);
                raiseException(runner, pair, eData);
            }
        }
    }


    private Element getCaseData(String caseID) {
        String data;
        try {
             data = _wService.getEngineClient().getCaseData(caseID);
        }
        catch (IOException ioe) {
             data = "<caseData/>";
        }
        return JDOMUtil.stringToElement(data);
    }


    /**
     * Raises an exception (triggered via the API) using the conclusion passed in
     * @param wir the wir to raise the exception for
     * @param ruleType the exception rule type
     * @param conclusion a populated conclusion object
     */
    public String raiseException(WorkItemRecord wir, RuleType ruleType,
                               RdrConclusion conclusion) {
        RdrNode dummyNode = new RdrNode();
        dummyNode.setConclusion(conclusion);
        RdrPair dummyPair = new RdrPair(dummyNode, dummyNode);
        raiseException(wir, dummyPair, null, ruleType);
        return StringUtil.wrap(ruleType.toLongString() +
                " exception raised for work item: " + wir.getID(), "result");
    }


    /**
     *  Replaces a running worklet case with another worklet case after an
     *  amendment to the ruleset for this exception.
     *  Called by WorkletGateway after a call from the Editor that the ruleset
     *  has been updated.
     *  Overrides the WorkletService equivalent - This one looks after exceptions,
     *  that one looks after selections
     *
     *  @param xType - the type of exception that launched the worklet
     *  @param caseID - the id of the original checked out case
     *  @param itemID - the id of the original checked out workitem
     *  @return a string of messages describing the success or otherwise of
     *          the process
     */
    public String replaceWorklet(RuleType xType, String caseID, String itemID)
            throws IOException {

        synchronized (_mutex) {
            _log.info("REPLACE EXLET EVENT");

            caseID = getIntegralID(caseID);

            // get the ExletRunner for the exception
            ExletRunner runner = _runners.getRunner(xType, caseID, itemID);
            if (runner == null) {
                raise("No exlets running for case: " + caseID);
            }

            // cancel any compensatory worklets running for the case/workitem
            cancelWorkletsForRunner(runner);

            // remove runner from running exlet handlers
            _runners.remove(runner);

            // go through the process again, depending on the exception type
            reevaluate(runner, xType);

            // get the new ExletRunner for the new exception
            runner = _runners.getRunner(xType, caseID, itemID);
            if (runner == null) {
                raise("Failed to start new exlet runner for case: " + caseID);
            }

            return _wService.getRunnerCaseIdList(runner.getWorkletRunners());
        }
    }


    private void reevaluate(ExletRunner runner, RuleType xType) {
        _log.debug("Reevaluating exception for revised ruleset");
        String caseID = runner.getCaseID();
        String trigger = runner.getTrigger();
        Element data = getCaseData(caseID);
        WorkItemRecord wir = xType.isItemLevelType() ? runner.getWir() : null;
        YSpecificationID specID = getSpecIDForCaseID(caseID);
        switch (xType) {
            case CasePreconstraint : checkConstraints(specID, caseID, data, true); break;
            case CasePostconstraint: checkConstraints(specID, caseID, data, false); break;
            case ItemPreconstraint : checkConstraints(wir, data, true); break;
            case ItemPostconstraint: checkConstraints(wir, data, false); break;
            case ItemAbort         :
                if (wir != null) handleWorkItemAbortException(wir,
                                    wir.getDataListString()); break ;
            case ItemTimeout :
                if (wir != null) handleTimeout(wir); break ;
            case ItemResourceUnavailable : break;   // todo
            case ItemConstraintViolation :
                if (wir != null) handleConstraintViolationException(wir,
                    wir.getDataListString()); break;
            case CaseExternalTrigger :
                raiseExternalException("case", caseID, trigger); break;
            case ItemExternalTrigger :
                raiseExternalException("item", caseID, trigger); break;
        }
    }


    /** returns true if case specified is a worklet instance */
    public boolean isWorkletCase(String caseID) {
        return (_runners.isCompensationWorklet(caseID));
    }


    /** stub method called from RdrConditionFunctions class */
    public String getStatus(String taskID) {
        return null;
    }


    /** restores the contents of the running datasets after a web server restart */
    private void restoreDataSets() {
        _runners.restore();
    }


    /**
     * Adds the contents of the workitem record to the case data for this case - thus
     * providing information about the workitem to the ruleset
     * @param wir - the wir being tested for an exception
     */
    private Element augmentItemData(WorkItemRecord wir, String dataStr) {
        Element data = !StringUtil.isNullOrEmpty(dataStr) ?
                JDOMUtil.stringToElement(dataStr) :
                new Element("data");

        // blend in any case level data not extant in the work item's data
     //   data = blendInCaseData(wir, data);

        //convert the wir contents to an Element
        Element eWir = JDOMUtil.stringToElement(wir.toXML()).detach();

        Element eInfo = new Element("process_info");     // new Element for info
        eInfo.addContent(eWir);                          // add the wir
        data.addContent(eInfo);                          // add element to case data
        return data;
    }


    private Element blendInCaseData(WorkItemRecord wir, Element itemData) {
        if (itemData == null) return null;
        Element caseData = getCaseData(wir.getRootCaseID());
        if (caseData != null) {
            Iterator<Element> itr = caseData.getChildren().iterator();  // avoid ConModEx
            while (itr.hasNext()) {
                Element child = itr.next();
                String name = child.getName();

                // assumption: if itemData contains an element of the same name as
                // caseData, then the itemData element's value is the same as, or newer
                // than, the caseData element's value. Therefore, only missing elements
                // should be added
                if (itemData.getChild(name) == null) {
                    itemData.addContent(child.clone());
                }
            }
        }
        return itemData;
    }


    /**
     *  Adds an external exception trigger to the case data so that the correct
     *  RDR can be found for it
     * @param triggerValue - the string value of the external trigger
     */
    private Element augmentExternalData(Element data, String triggerValue) {

        // all external triggers must be delimited with "'s
        if (! triggerValue.startsWith("\""))
            triggerValue = "\"" + triggerValue + "\"" ;

        Element eTrigger = new Element("trigger");        // new Element for trigger
        eTrigger.addContent(triggerValue);                // add the text
        data.addContent(eTrigger);                   // add element to case data
        return data;
    }


    protected void raise(String msg) throws IOException {
        _log.error(msg);
        throw new IOException(msg);
    }


} // end of ExceptionService class
