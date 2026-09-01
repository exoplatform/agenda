import fs from 'fs';
import path from 'path';
import {mount} from '@vue/test-utils';
import AgendaEventFormAttendeesDrawer from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormAttendeesDrawer.vue';

/*
 * EXO-89852, the participants drawer.
 *
 * Benjamin: "when i click + to add a participant, it opens the drawer and I
 * have to click add to add a user in the list. An unnecessary click IMO."
 *
 * The search field was hidden behind a "+ Add" button whose entire statement
 * was showSuggester = true. That button was not a decision anybody made about
 * adding participants — it was a consequence of layout: the add control and
 * the filter shared one line, so one of them had to fold away. Give each its
 * own line and the fold has nothing left to do.
 *
 * What these pins hold, in order of what a user would notice:
 *
 *   1. Opening the drawer puts the search field on screen. No button first.
 *   2. Nothing renders the old "+ Add" button any more.
 *   3. The filter is not a sibling of the search field on one row — that
 *      sharing is what created the button, and a future change putting them
 *      back together would recreate it.
 *   4. A read-only viewer gains no search field. The drawer serves both modes
 *      and only one of them may add anybody.
 */

/**
 * An attendee as the event holds one.
 *
 * @param {String} remoteId the username
 * @param {String} fullname how the person is named
 * @param {String} response their answer, if any
 * @returns {Object} the attendee
 */
function attendee(remoteId, fullname, response) {
  return {
    identity: {id: remoteId, providerId: 'organization', remoteId, profile: {fullname}},
    response: response || 'NEEDS_ACTION',
  };
}

/**
 * An event with a resolved destination, so the suggester is not disabled for
 * want of one.
 *
 * @param {Array} attendees who is already invited
 * @returns {Object} the event
 */
function eventWith(attendees) {
  return {
    calendar: {owner: {id: '7', providerId: 'space', remoteId: 'marketing'}},
    creator: {id: '7'},
    attendees: attendees || [],
  };
}

/**
 * Mounts the drawer.
 *
 * @param {Object} options `{event, editable}`
 * @returns {Object} the mounted wrapper
 */
function mountDrawer(options) {
  const settings = options || {};
  return mount(AgendaEventFormAttendeesDrawer, {
    propsData: {
      event: settings.event || eventWith([attendee('sara', 'Sara Green')]),
      editable: settings.editable !== false,
    },
    mocks: {
      $t: key => key,
      $suggesterService: {convertSuggesterItemToIdentity: item => item},
    },
  });
}

/*
 * The drawer body's rows, named by something only that row contains. Same
 * reader as the quick-add drawer's order pin, and for the same reason: a row
 * it does not recognise comes back named rather than skipped, so a control
 * appended in the wrong place cannot pass unnoticed.
 *
 * @param {Object} drawer the mounted drawer
 * @returns {Array} the row names, top to bottom
 */
function bodyRowOrder(drawer) {
  const body = drawer.element.querySelector('.pa-4');
  return Array.prototype.map.call(body.children, row => {
    if (row.querySelector('exo-identity-suggester')) {
      return 'suggester';
    } else if (row.querySelector('agenda-event-form-attendee-item')) {
      /*
       * Named before any button test: the list row grows a "load more" button
       * past twenty attendees, and a reader that asked about buttons first
       * would call that row the filter.
       */
      return 'attendeeList';
    } else if (row.querySelector('v-text-field')) {
      return 'filterBar';
    } else if (row.querySelector('v-btn')) {
      return 'filterToggle';
    }
    return `unrecognised:${row.tagName.toLowerCase()}`;
  });
}

/*
 * EXO-89852, the three strings the participants suggester shows.
 *
 * The field's placeholder read "Search for space or users". Wrong twice over:
 * it omitted the EMAIL ADDRESS the field has always accepted — typed straight
 * in and committed on Enter, space or blur by checkGuestInvitation and
 * saveGuestEmail — and it led with the space, the rarest of the three, ahead
 * of the user, which is what people reach for.
 *
 * Two kinds of pin, because two different edits can break this:
 *
 *   - the drawer must read the key it means to read. Three labels go to three
 *     different places in the suggester and two of the keys are shared with
 *     suggesters that search AGENDAS, so wiring the wrong one is both easy and
 *     invisible on the screen that did not change.
 *   - the English must still name all three things the field takes. That is
 *     the defect itself, and only the bundle can be asked about it.
 */
describe('EXO-89852 — the participants field says what it takes', () => {
  /**
   * The shipped _en bundle, as a key/value map.
   *
   * Read from the file rather than mocked: the defect being fixed lives in the
   * bundle, and a test that mocked $t could not see it.
   *
   * @returns {Object} every key in Agenda_en.properties
   */
  function englishBundle() {
    const file = path.join(__dirname, '../../main/resources/locale/portlet/Agenda_en.properties');
    const bundle = {};
    fs.readFileSync(file, 'utf8').split('\n').forEach(line => {
      const at = line.indexOf('=');
      if (at > 0 && !line.startsWith('#')) {
        bundle[line.slice(0, at)] = line.slice(at + 1);
      }
    });
    return bundle;
  }

  it('sends each label to the slot it belongs in', () => {
    const labels = mountDrawer().vm.participantSuggesterLabels;

    expect(labels.placeholder).toBe('agenda.attendees.searchPlaceholder');
    expect(labels.searchPlaceholder).toBe('agenda.searchPlaceholder');
    expect(labels.noDataLabel).toBe('agenda.attendees.noDataLabel');
  });

  /*
   * The no-data line is the drawer's own key, not the one two agenda
   * suggesters share: "No agenda found" is right for them and wrong here.
   */
  it('does not borrow the agenda suggesters\' no-data line', () => {
    const labels = mountDrawer().vm.participantSuggesterLabels;

    expect(labels.noDataLabel).not.toBe('agenda.noDataLabel');
    expect(englishBundle()['agenda.noDataLabel']).toBe('No agenda found');
  });

  it('names all three things the field accepts, in the order they are reached for', () => {
    const placeholder = englishBundle()['agenda.attendees.searchPlaceholder'];

    expect(placeholder).toBeDefined();
    expect(placeholder.toLowerCase()).toContain('user');
    expect(placeholder.toLowerCase()).toContain('email');
    expect(placeholder.toLowerCase()).toContain('space');
    expect(placeholder.toLowerCase().indexOf('user')).toBeLessThan(placeholder.toLowerCase().indexOf('email'));
    expect(placeholder.toLowerCase().indexOf('email')).toBeLessThan(placeholder.toLowerCase().indexOf('space'));
  });

  it('answers an empty search in terms of what was searched for', () => {
    const noData = englishBundle()['agenda.attendees.noDataLabel'];

    expect(noData).toBeDefined();
    expect(noData.toLowerCase()).not.toContain('agenda');
  });
});

describe('EXO-89852 — the participants drawer opens on its search field', () => {
  it('shows the search field as soon as the drawer renders, with no button in the way', () => {
    const drawer = mountDrawer();

    expect(drawer.find('exo-identity-suggester').exists()).toBe(true);
    expect(drawer.text()).not.toContain('agenda.label.addParticipants');
  });

  /*
   * The button is gone as a thing, not merely hidden: asserted on the message
   * key it carried AND on the absence of any control that would reveal the
   * field, so reintroducing it under another label still fails.
   */
  it('has no control whose job is to reveal the search field', () => {
    const drawer = mountDrawer();
    const suggesterRow = drawer.element.querySelector('exo-identity-suggester').closest('form');

    expect(suggesterRow).not.toBeNull();
    expect(suggesterRow.querySelector('v-btn')).toBeNull();
  });

  /*
   * The row-sharing IS the defect. Two controls on one line is what made one
   * of them a toggle, so a change putting them back on one row must fail here
   * even if the button itself is never restored.
   */
  it('keeps the filter off the search field\'s row', () => {
    const order = bodyRowOrder(mountDrawer());

    expect(order).toEqual(['suggester', 'filterToggle', 'attendeeList']);
    expect(order.indexOf('suggester')).toBeLessThan(order.indexOf('filterToggle'));
  });

  it('puts the cursor in the search field on every opening, not only the first', () => {
    const drawer = mountDrawer();
    const focused = [];
    drawer.vm.$refs.attendeesDrawer = {open: () => {}};
    drawer.vm.$refs.invitedAttendeeAutoComplete = {focus: () => focused.push('focus')};

    drawer.vm.open();
    return drawer.vm.$nextTick()
      .then(() => {
        drawer.vm.open();
        return drawer.vm.$nextTick();
      })
      .then(() => expect(focused).toEqual(['focus', 'focus']));
  });

  it('gives a read-only viewer no way to add anybody', () => {
    const readOnly = mountDrawer({editable: false});

    expect(readOnly.find('exo-identity-suggester').exists()).toBe(false);
    expect(readOnly.find('form').exists()).toBe(false);
    expect(readOnly.text()).toContain('agenda.label.searchParticipant');
  });

  /*
   * The filter keeps its toggle — it was not folded away with the add button,
   * because it hides a field and a dropdown most drawers do not need. What it
   * no longer does is take the search field's place when it expands.
   */
  it('expands the filter without displacing the search field', () => {
    const drawer = mountDrawer();
    drawer.vm.openFilterBar();

    return drawer.vm.$nextTick().then(() => {
      expect(drawer.find('exo-identity-suggester').exists()).toBe(true);
      expect(bodyRowOrder(drawer)).toEqual(['suggester', 'filterBar', 'attendeeList']);
    });
  });
});
