import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaEventFormDates from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDates.vue';
import Agenda from '../../main/webapp/vue-app/agenda/components/Agenda.vue';
import AgendaTimelineWidget from '../../main/webapp/vue-app/agenda-timeline/components/AgendaTimelineWidget.vue';

/*
 * The regression this file exists for: EXO-89842, "three agenda views turn an
 * unreachable calendar account into an empty one".
 *
 * Each of the three views asked every signed-in account for its events and
 * caught a failed read into `{connector, events: []}`. From that point the
 * view held an account that had answered "nothing" where it really had an
 * account that could not be asked, and nothing downstream could tell the two
 * apart — the server degrades a failed read to an empty list, so emptiness is
 * not evidence either.
 *
 * The pin is therefore never "an error was caught". It is the DISTINCTION:
 * the same view, fed a genuinely empty account and fed an unreachable one,
 * must not end up in the same state. Every assertion below is written as that
 * contrast, so a mutant that erases the distinction fails on the assertion
 * rather than on an exception.
 */

const REACHABLE = {name: 'caldav', user: 'jane@example.com', rank: 1};
const UNREACHABLE = {name: 'google', user: 'jane@gmail.com', rank: 2};

/**
 * One event as an account hands it over, before the view converts its dates.
 *
 * @param {String} id the remote event identifier
 * @returns {Object} a remote event
 */
function remoteEvent(id) {
  return {
    id,
    summary: `event ${id}`,
    start: '2026-09-01T09:00:00Z',
    end: '2026-09-01T10:00:00Z',
  };
}

/**
 * An account that answers with the given events.
 *
 * @param {Object} connector the connector descriptor
 * @param {Array} events what the account holds, possibly none
 * @returns {Object} a signed-in connector stub
 */
function answering(connector, events) {
  return Object.assign({}, connector, {
    isSignedIn: true,
    getEvents: () => Promise.resolve(events),
  });
}

/**
 * An account that cannot be reached at all.
 *
 * @param {Object} connector the connector descriptor
 * @returns {Object} a signed-in connector stub whose read rejects
 */
function unreachable(connector) {
  return Object.assign({}, connector, {
    isSignedIn: true,
    getEvents: () => Promise.reject(new Error('unreachable')),
  });
}

/*
 * The pieces of application state the three retrieval methods read, shared so
 * a spec states only what it varies. $agendaUtils is the SHIPPED module, not a
 * stub: the split of answers from failures is part of what is under test.
 */
const baseVm = {
  loading: false,
  remoteEvents: [],
  failedConnectors: [],
  /*
   * EXO-89851 gave the form a second kind of unreadable source — a calendar,
   * beside an account — and the one warning now names both. Declared here so
   * these fixtures keep describing the component as it really is: a `|| []`
   * in the computed would have made them pass while still describing
   * something the component does not hold.
   */
  failedStoreSourceKeys: [],
  $t: key => key,
  period: {start: new Date('2026-09-01T00:00:00Z'), end: new Date('2026-09-08T00:00:00Z')},
  $agendaUtils: agendaUtils,
};

/**
 * Runs a component's shipped remote-events retrieval against a set of
 * accounts and returns the state it left behind.
 *
 * @param {Object} component the component whose method to run
 * @param {Array} connectors the signed-in accounts to ask
 * @param {Object} extraState state that component needs on top of the base
 * @returns {Promise} resolves with the resulting vm
 */
function retrieve(component, connectors, extraState) {
  const vm = Object.assign({}, baseVm, {
    signedInConnectors: connectors,
    connectorStatus: 1,
    settingsLoaded: true,
    refreshEventsToDisplay: () => {},
  }, extraState || {});
  component.methods.retrieveRemoteEvents.call(vm);
  // the reads are already-settled promises; two turns of the microtask queue
  // let Promise.all and its .then/.finally run to completion
  return Promise.resolve().then(() => Promise.resolve()).then(() => Promise.resolve()).then(() => vm);
}

/**
 * Evaluates a component's failure computeds over a vm.
 *
 * @param {Object} component the component whose computeds to evaluate
 * @param {Object} vm the state to evaluate them against
 * @returns {Object} {names, hasFailedSource}
 */
function failureState(component, vm) {
  const names = component.computed.failedSourceNames.call(vm);
  return {
    names,
    hasFailedSource: component.computed.hasFailedSource.call({failedSourceNames: names}),
  };
}

describe('AgendaUtils splits answers from failures', () => {
  it('drops a failed account from the events without turning it into an empty answer', () => {
    const split = agendaUtils.splitRemoteEventResults([
      {connector: REACHABLE, events: [remoteEvent('a')]},
      {connector: UNREACHABLE, failed: true},
    ]);
    expect(split.events).toHaveLength(1);
    expect(split.failedConnectors).toEqual([UNREACHABLE]);
  });

  it('reports no failure when an account genuinely holds nothing', () => {
    const split = agendaUtils.splitRemoteEventResults([
      {connector: REACHABLE, events: [remoteEvent('a')]},
      {connector: UNREACHABLE, events: []},
    ]);
    // the contrast that carries the whole fix: same visible events as the
    // case above, and it must NOT be reported as a failure
    expect(split.events).toHaveLength(1);
    expect(split.failedConnectors).toEqual([]);
  });

  it('names an account by the address it is signed in as', () => {
    expect(agendaUtils.failedSourceNames([UNREACHABLE])).toEqual(['jane@gmail.com']);
    expect(agendaUtils.failedSourceNames([{name: 'exchange'}])).toEqual(['exchange']);
    expect(agendaUtils.failedSourceNames([])).toEqual([]);
  });
});

describe('the event form does not paint an unreachable account as a free slot', () => {
  it('records the account that could not be reached, and warns about the slot', () => {
    return retrieve(AgendaEventFormDates, [
      answering(REACHABLE, [remoteEvent('a')]),
      unreachable(UNREACHABLE),
    ]).then(vm => {
      expect(vm.remoteEvents).toHaveLength(1);
      const state = failureState(AgendaEventFormDates, vm);
      expect(state.hasFailedSource).toBe(true);
      expect(state.names).toEqual(['jane@gmail.com']);
      // the sentence the user reads has to name the account and say what the
      // gap costs them, since this grid is what they pick a slot against
      const warning = AgendaEventFormDates.computed.failedSourceWarning.call({
        failedSourceNames: state.names,
        $t: (key, args) => `${key}|${args[0]}`,
      });
      expect(warning).toBe('agenda.eventForm.busyTimesIncomplete|jane@gmail.com');
    });
  });

  it('stays silent when every account answered, one of them with nothing', () => {
    return retrieve(AgendaEventFormDates, [
      answering(REACHABLE, [remoteEvent('a')]),
      answering(UNREACHABLE, []),
    ]).then(vm => {
      expect(vm.remoteEvents).toHaveLength(1);
      // identical grid to the case above — and no warning, because here the
      // emptiness IS the answer
      expect(failureState(AgendaEventFormDates, vm).hasFailedSource).toBe(false);
    });
  });

  it('keeps the events the reachable accounts returned when another fails', () => {
    return retrieve(AgendaEventFormDates, [
      answering(REACHABLE, [remoteEvent('a'), remoteEvent('b')]),
      unreachable(UNREACHABLE),
    ]).then(vm => {
      expect(vm.remoteEvents.map(event => event.id)).toEqual(['a', 'b']);
    });
  });

  it('clears a past failure once no account is signed in', () => {
    return retrieve(AgendaEventFormDates, [unreachable(UNREACHABLE)])
      .then(vm => {
        expect(failureState(AgendaEventFormDates, vm).hasFailedSource).toBe(true);
        return retrieve(AgendaEventFormDates, [], {failedConnectors: vm.failedConnectors});
      })
      .then(vm => {
        expect(failureState(AgendaEventFormDates, vm).hasFailedSource).toBe(false);
      });
  });
});

describe('the calendar view says when a source dropped out', () => {
  it('records the unreachable account and names it under the header', () => {
    return retrieve(Agenda, [
      answering(REACHABLE, [remoteEvent('a')]),
      unreachable(UNREACHABLE),
    ]).then(vm => {
      expect(vm.remoteEvents).toHaveLength(1);
      const state = failureState(Agenda, vm);
      expect(state.hasFailedSource).toBe(true);
      const message = Agenda.computed.failedSourceMessage.call({
        failedSourceNames: state.names,
        $t: (key, args) => `${key}|${args[0]}`,
      });
      expect(message).toBe('agenda.calendarIncomplete|jane@gmail.com');
    });
  });

  it('stays silent when every account answered, one of them with nothing', () => {
    return retrieve(Agenda, [
      answering(REACHABLE, [remoteEvent('a')]),
      answering(UNREACHABLE, []),
    ]).then(vm => {
      expect(vm.remoteEvents).toHaveLength(1);
      expect(failureState(Agenda, vm).hasFailedSource).toBe(false);
    });
  });

  it('names every account that failed, not only the first', () => {
    return retrieve(Agenda, [
      unreachable(REACHABLE),
      unreachable(UNREACHABLE),
    ]).then(vm => {
      expect(failureState(Agenda, vm).names).toEqual(['jane@example.com', 'jane@gmail.com']);
    });
  });
});

describe('the timeline widget says its list is short of a source', () => {
  it('records the unreachable account and names it in the tooltip', () => {
    return retrieve(AgendaTimelineWidget, [
      answering(REACHABLE, [remoteEvent('a')]),
      unreachable(UNREACHABLE),
    ]).then(vm => {
      expect(vm.remoteEvents).toHaveLength(1);
      const state = failureState(AgendaTimelineWidget, vm);
      expect(state.hasFailedSource).toBe(true);
      const title = AgendaTimelineWidget.computed.failedSourceTitle.call({
        failedSourceNames: state.names,
        $t: (key, args) => `${key}|${args[0]}`,
      });
      expect(title).toBe('agenda.timeline.someEventsMissingTooltip|jane@gmail.com');
    });
  });

  it('stays silent when every account answered, one of them with nothing', () => {
    return retrieve(AgendaTimelineWidget, [
      answering(REACHABLE, [remoteEvent('a')]),
      answering(UNREACHABLE, []),
    ]).then(vm => {
      expect(vm.remoteEvents).toHaveLength(1);
      expect(failureState(AgendaTimelineWidget, vm).hasFailedSource).toBe(false);
    });
  });
});
