import {shallowMount} from '@vue/test-utils';

import AgendaEventFormAttendeesDrawer
  from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormAttendeesDrawer.vue';
import AgendaEventFormAttendees
  from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormAttendees.vue';

/*
 * EXO-89853 — nobody could be invited to an event in a personal calendar.
 *
 * The drawer handed `spaceURL: <calendar owner remoteId>` to the suggester
 * unconditionally. For a space calendar that is the space pretty name and the
 * search correctly returns its members; for a personal calendar it is a
 * *username*, and social's PeopleRestService answers an empty list as soon as
 * the value names no space (it returns early when the pretty name resolves to
 * no space — the behaviour its own PeopleRestServiceTest pins with
 * `spaceURL=NOT_EXISTING_SPACE` asserting 0 results, and, in the same test,
 * *no* spaceURL parameter asserting a non-empty one).
 *
 * So the scope must be applied only when the owner really is a space. These
 * specs pin both directions, because either one alone is worthless: relaxing
 * the space case would widen a space event's suggestions, and keeping the
 * scope on a personal calendar is the bug itself.
 */

/*
 * The drawer puts everything inside `exo-drawer`'s named "content" slot. A
 * default stub renders no slot at all, so the suggester under test would never
 * exist — this stub renders the slots instead.
 */
const ExoDrawerStub = {
  name: 'exo-drawer',
  template: '<div class="drawer-stub"><slot name="title"></slot><slot name="content"></slot></div>',
};

/*
 * Keeps the props it is handed readable, so a test can pin the search scope
 * the drawer actually passes rather than re-deriving it.
 */
const ExoIdentitySuggesterStub = {
  name: 'exo-identity-suggester',
  props: ['searchOptions', 'ignoreItems', 'labels', 'disabled', 'title'],
  template: '<div class="suggester-stub"></div>',
};

/*
 * Stands in for the shared attendees drawer, so a test can pin which event
 * object the hosting field handed it.
 */
const AttendeesDrawerStub = {
  name: 'agenda-event-form-attendees-drawer',
  props: ['event'],
  template: '<div class="attendees-drawer-stub"></div>',
};

/**
 * A calendar owner as the Agenda REST layer builds it for a space:
 * IdentityEntity.providerId 'space', remoteId the space pretty name.
 *
 * @returns {Object} a space calendar owner identity
 */
function spaceOwner() {
  return {
    id: 'space:marketing',
    providerId: 'space',
    remoteId: 'marketing',
    space: {
      id: '42',
      displayName: 'Marketing',
      prettyName: 'marketing',
    },
  };
}

/**
 * A calendar owner as the Agenda REST layer builds it for a user — a personal
 * calendar. `remoteId` is the *username*, which names no space.
 *
 * @returns {Object} a personal calendar owner identity
 */
function userOwner() {
  return {
    id: '7',
    providerId: 'organization',
    remoteId: 'jsmith',
    profile: {
      id: '7',
      username: 'jsmith',
      fullname: 'John Smith',
    },
  };
}

/**
 * Mounts the attendees drawer with the suggester visible and returns the
 * search options it handed to that suggester.
 *
 * @param {Object} owner the calendar owner the event hangs from
 * @returns {Object} the `search-options` prop received by the suggester
 */
function searchOptionsFor(owner) {
  const wrapper = shallowMount(AgendaEventFormAttendeesDrawer, {
    propsData: {
      event: {
        calendar: {owner},
        attendees: [],
      },
      editable: true,
    },
    data() {
      return {showSuggester: true};
    },
    stubs: {
      'exo-drawer': ExoDrawerStub,
      'exo-identity-suggester': ExoIdentitySuggesterStub,
    },
    mocks: {
      $t: key => key,
    },
  });

  const suggester = wrapper.findComponent(ExoIdentitySuggesterStub);
  expect(suggester.exists()).toBe(true);
  return suggester.props('searchOptions');
}

describe('Attendee suggester scope', () => {
  it('scopes a space calendar to that space, as it always has', () => {
    const options = searchOptionsFor(spaceOwner());

    // The space case must not move: this is a guard on *when* the scope
    // applies, never a relaxation of it.
    expect(options.spaceURL).toBe('marketing');
  });

  it('does not scope a personal calendar to anything', () => {
    const options = searchOptionsFor(userOwner());

    // No space scope at all — that is what makes the endpoint suggest the
    // viewer's connections and then the other users.
    expect(options.spaceURL).toBeUndefined();

    // And, whatever the key, the username must not travel as a search scope:
    // it names no space, and any scope naming no space suggests nobody.
    expect(Object.values(options)).not.toContain('jsmith');
  });

  it('still scopes a space calendar carrying no providerId', () => {
    const owner = spaceOwner();
    delete owner.providerId;

    // Deliberately belt and braces: an owner carrying a space entity is a
    // space whatever else it carries. The disjunct can only ever *keep* the
    // scope, never widen one — and widening a space event is the one outcome
    // this change must not produce.
    expect(searchOptionsFor(owner).spaceURL).toBe('marketing');
  });

  it('does not scope an event whose calendar owner is not known yet', () => {
    const options = searchOptionsFor(undefined);

    expect(options.spaceURL).toBeUndefined();
  });
});

describe('Every attendee field is served by the one drawer', () => {
  /*
   * The desktop form, the quick-add drawer, the mobile form and the content
   * publication form each render `agenda-event-form-attendees`, which owns the
   * single `agenda-event-form-attendees-drawer` fixed above. This pins that
   * chain, so the fix cannot land on one screen while the others still find
   * nobody.
   */
  it('hands the event straight to the shared attendees drawer', () => {
    const event = {calendar: {owner: userOwner()}, attendees: []};
    const wrapper = shallowMount(AgendaEventFormAttendees, {
      propsData: {event},
      stubs: {
        'agenda-event-form-attendees-drawer': AttendeesDrawerStub,
      },
      mocks: {
        $t: key => key,
        $userService: {getUser: () => Promise.resolve({})},
      },
    });

    const drawer = wrapper.findComponent(AttendeesDrawerStub);
    expect(drawer.exists()).toBe(true);
    expect(drawer.props('event')).toBe(event);
  });
});
