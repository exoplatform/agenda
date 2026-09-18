/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import Vue from 'vue';
import {shallowMount} from '@vue/test-utils';

import AgendaCalendarShareDrawer from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaCalendarShareDrawer.vue';

/**
 * The calendar share drawer (EXO-90357): what the owner sees — one list of
 * colleagues, nothing about where a share was delivered, the access held on
 * a server outside eXo — and what each action does.
 */
describe('Calendar share drawer', () => {

  Vue.config.ignoredElements = [/^v-/, 'exo-user-avatar', 'exo-identity-suggester'];

  const CALENDAR = {id: 10, name: 'Work', acl: {canShare: true}};

  const ALICE = {calendarId: 10, shareeIdentityId: 3, username: 'alice', displayName: 'Alice Liddell', source: 'EXO', access: 'VIEW', deliveredTo: 'caldav:1', disabled: false};

  /** Shared in eXo only: no channel carried it, which the row never says. */
  const BOB = {calendarId: 10, shareeIdentityId: 4, username: 'bob', displayName: 'Bob Builder', source: 'EXO', access: 'EDIT', deliveredTo: null, disabled: false};

  const OUTSIDE = {channelId: 'caldav:1', externalId: 'grant-9', kind: 'OUTSIDE_EXO', shareeIdentityId: 0, displayName: '_SERVICE', email: 'exo.service@y.org', removable: true, readOnly: false};

  const EVERYONE = {channelId: 'caldav:1', externalId: '{DAV:}all', kind: 'EVERYONE', shareeIdentityId: 0, displayName: null, removable: false, readOnly: true};

  let service;

  let drawerStub;

  let confirmStub;

  let menuStub;

  let suggesterStub;

  let autocomplete;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the drawer with eXo's $t and the service mocked.
   *
   * @returns {Object} the wrapper
   */
  function mountDrawer() {
    const wrapper = shallowMount(AgendaCalendarShareDrawer, {
      mocks: {
        // A share refusal code is worded by the bundle; the drawer falls back
        // to its generic sentence for a code the bundle does not know
        $t: (key, args) => (args && `${key}(${Object.values(args).join('|')})`) || ((key.startsWith('agenda.share.') || key.startsWith('agenda.calendarShare.kind.')) ? `${key} (worded)` : key),
        $calendarShareService: service,
      },
      stubs: {
        'exo-drawer': drawerStub,
        'exo-confirm-dialog': confirmStub,
        'exo-identity-suggester': suggesterStub,
        // The level control is a v-menu whose activator is a scoped slot; an
        // ignored element would render neither, so it is stubbed to render
        // both the trigger and the list
        'v-menu': menuStub,
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    return wrapper;
  }

  /**
   * Opens the drawer on the calendar and waits for its listing.
   *
   * @param {Object} wrapper the wrapper
   * @returns {Promise} resolved once listed
   */
  async function open(wrapper) {
    wrapper.vm.$root.$emit('agenda-calendar-share-drawer-open', CALENDAR);
    await flush();
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {language: 'en', userName: 'owner'}}};
    global.extensionRegistry = {loadExtensions: () => []};
  });

  beforeEach(() => {
    service = {
      getShares: jest.fn().mockResolvedValue({shares: [ALICE, BOB], externalShares: [OUTSIDE, EVERYONE]}),
      share: jest.fn(),
      setLevel: jest.fn().mockResolvedValue(),
      unshare: jest.fn().mockResolvedValue(),
      removeExternalShare: jest.fn().mockResolvedValue(),
    };
    drawerStub = {
      template: '<div class="drawer-stub"><slot name="title"></slot><slot name="content"></slot><slot name="footer"></slot></div>',
      methods: {
        open: jest.fn(),
        close: jest.fn(),
      },
    };
    confirmStub = {
      template: '<div class="confirm-stub"></div>',
      methods: {
        open: jest.fn(),
      },
    };
    // The level control is a v-menu whose activator is a scoped slot; an
    // ignored element would render neither it nor the list, so it is stubbed
    // to render both
    menuStub = {
      template: '<div class="menu-stub"><slot name="activator" v-bind="{on: {}, attrs: {}}"></slot><slot></slot></div>',
    };
    // The real suggester wraps a v-autocomplete under this ref; the drawer
    // reaches it to empty the value the autocomplete keeps, as a chip, once
    // the suggester's own model is cleared
    autocomplete = {internalValue: {id: 'organization:dave'}};
    suggesterStub = {
      template: '<div class="suggester-stub"></div>',
      created() {
        this.$refs.selectAutoComplete = autocomplete;
      },
    };
  });

  it('lists the colleagues as plain rows, whether or not a server also carries the share', async () => {
    const wrapper = mountDrawer();

    await open(wrapper);

    expect(drawerStub.methods.open).toHaveBeenCalled();
    const rows = wrapper.findAll('.agenda-calendar-sharee');
    expect(rows).toHaveLength(2);
    expect(rows.at(0).find('.agenda-calendar-sharee-name').text()).toContain('Alice Liddell');
    expect(rows.at(1).find('.agenda-calendar-sharee-name').text()).toContain('Bob Builder');
    // Each row says its own level (EXO-90378), and it is the one control that
    // changes it: a record with no level reads as Can view
    expect(rows.at(0).find('.agenda-calendar-share-access').text()).toContain('agenda.calendarShare.access.view');
    expect(rows.at(1).find('.agenda-calendar-share-access').text()).toContain('agenda.calendarShare.access.edit');
    rows.wrappers.forEach(row => {
      expect(row.find('.agenda-calendar-share-unshare').exists()).toBe(true);
      expect(row.find('.v-chip').exists()).toBe(false);
      expect(row.text()).not.toMatch(/deliver|retry|also on/i);
    });
  });

  it('lists the access held outside eXo apart, read-only, with its level and the address', async () => {
    const wrapper = mountDrawer();

    await open(wrapper);

    expect(wrapper.find('.agenda-calendar-share-sharees-title').text()).toBe('agenda.calendarShare.sharedWith');
    expect(wrapper.find('.agenda-calendar-share-external-title').text()).toBe('agenda.calendarShare.alsoHasAccess');
    const external = wrapper.findAll('.agenda-calendar-external-share');
    expect(external).toHaveLength(2);
    expect(external.at(0).find('.agenda-calendar-external-name').text()).toContain('_SERVICE');
    expect(external.at(0).find('.agenda-calendar-external-email').text()).toBe('exo.service@y.org');
    expect(external.at(0).find('.agenda-calendar-share-access').text()).toBe('agenda.calendarShare.access.edit');
    expect(external.at(0).find('.agenda-calendar-share-remove-external').exists()).toBe(true);
    expect(external.at(1).find('.agenda-calendar-external-name').text()).toBe('agenda.calendarShare.kind.EVERYONE (worded)');
    expect(external.at(1).find('.agenda-calendar-share-access').text()).toBe('agenda.calendarShare.access.view');
    expect(external.at(1).find('.agenda-calendar-share-remove-external').exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/record/i);
    expect(wrapper.find('.agenda-calendar-share-none').exists()).toBe(false);
  });

  it('shares with the colleague the suggester picked, lists the answer, tells the rows and empties the field', async () => {
    const dave = {calendarId: 10, shareeIdentityId: 6, username: 'dave', displayName: 'Dave', source: 'EXO'};
    service.share.mockResolvedValue(dave);
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.setData({sharee: {id: 'organization:dave', remoteId: 'dave', providerId: 'organization'}});
    await flush();

    expect(service.share).toHaveBeenCalledWith(10, 'dave');
    expect(wrapper.findAll('.agenda-calendar-sharee')).toHaveLength(3);
    expect(wrapper.vm.sharee).toBeNull();
    expect(autocomplete.internalValue).toBeNull();
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-shares-changed');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarShare.shared(Dave)', 'success');
  });

  it('asks first when the calendar holds copies of the owner\'s eXo meetings, and shares on OK only', async () => {
    service.getShares.mockResolvedValue({shares: [], externalShares: [], meetingCopies: true});
    service.share.mockResolvedValue({calendarId: 10, shareeIdentityId: 6, username: 'dave', displayName: 'Dave', source: 'EXO'});
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.setData({sharee: {id: 'organization:dave', remoteId: 'dave', providerId: 'organization'}});
    await flush();

    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.share).not.toHaveBeenCalled();
    expect(wrapper.vm.confirmTitle).toBe('agenda.calendarShare.confirmMeetingCopies.title');
    expect(wrapper.vm.confirmMessage).toBe('');
    expect(wrapper.vm.confirmOkLabel).toBe('agenda.calendarShare.confirmMeetingCopies.ok');

    wrapper.vm.cancelled();
    expect(service.share).not.toHaveBeenCalled();

    await wrapper.setData({sharee: {id: 'organization:dave', remoteId: 'dave', providerId: 'organization'}});
    await flush();
    await wrapper.vm.confirmed();
    await flush();

    expect(service.share).toHaveBeenCalledTimes(1);
    expect(service.share).toHaveBeenCalledWith(10, 'dave');
  });

  it('shares without a question when the calendar holds no meeting copies', async () => {
    service.getShares.mockResolvedValue({shares: [], externalShares: [], meetingCopies: false});
    service.share.mockResolvedValue({calendarId: 10, shareeIdentityId: 6, username: 'dave', displayName: 'Dave', source: 'EXO'});
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.setData({sharee: {id: 'organization:dave', remoteId: 'dave', providerId: 'organization'}});
    await flush();

    expect(confirmStub.methods.open).not.toHaveBeenCalled();
    expect(service.share).toHaveBeenCalledWith(10, 'dave');
  });

  it('words a refusal under the field rather than in a snackbar', async () => {
    service.share.mockRejectedValue(new Error('agenda.share.shareeIsOwner'));
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.setData({sharee: {id: 'organization:owner', remoteId: 'owner', providerId: 'organization'}});
    await flush();

    expect(wrapper.find('.agenda-calendar-share-error').text()).toBe('agenda.share.shareeIsOwner (worded)');
    expect(wrapper.findAll('.agenda-calendar-sharee')).toHaveLength(2);
  });

  it('raises a colleague to Can edit at once, without a question', async () => {
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.vm.changeLevel(ALICE, 'EDIT');
    await flush();

    expect(service.setLevel).toHaveBeenCalledWith(10, 3, 'EDIT');
    expect(confirmStub.methods.open).not.toHaveBeenCalled();
    expect(wrapper.findAll('.agenda-calendar-sharee').at(0).find('.agenda-calendar-share-access').text())
      .toContain('agenda.calendarShare.access.edit');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-shares-changed');
  });

  it('asks before lowering a colleague to Can view, and says their events stay', async () => {
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.vm.changeLevel(BOB, 'VIEW');

    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.setLevel).not.toHaveBeenCalled();
    expect(wrapper.vm.confirmTitle).toBe('agenda.calendarShare.downgradeConfirmTitle');
    expect(wrapper.vm.confirmMessage).toBe('agenda.calendarShare.downgradeConfirmMessage(Bob Builder)');

    await wrapper.vm.confirmed();
    await flush();

    expect(service.setLevel).toHaveBeenCalledWith(10, 4, 'VIEW');
    expect(wrapper.findAll('.agenda-calendar-sharee').at(1).find('.agenda-calendar-share-access').text())
      .toContain('agenda.calendarShare.access.view');
  });

  it('forgets the colleague when the downgrade question is closed without OK', async () => {
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.vm.changeLevel(BOB, 'VIEW');
    wrapper.vm.cancelled();
    await flush();

    expect(service.setLevel).not.toHaveBeenCalled();
    expect(wrapper.vm.confirmTitle).toBe('agenda.calendarShare.unshareConfirmTitle');
    expect(wrapper.findAll('.agenda-calendar-sharee').at(1).find('.agenda-calendar-share-access').text())
      .toContain('agenda.calendarShare.access.edit');
  });

  it('picking the level a colleague already has writes nothing', async () => {
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.vm.changeLevel(ALICE, 'VIEW');
    await wrapper.vm.changeLevel(BOB, 'EDIT');
    await flush();

    expect(service.setLevel).not.toHaveBeenCalled();
    expect(confirmStub.methods.open).not.toHaveBeenCalled();
  });

  it('asks before revoking, then revokes and reads the list again', async () => {
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.findAll('.agenda-calendar-share-unshare').at(0).trigger('click');
    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.unshare).not.toHaveBeenCalled();

    service.getShares.mockResolvedValue({shares: [BOB], externalShares: [OUTSIDE, EVERYONE]});
    await wrapper.vm.unshare();
    await flush();

    expect(service.unshare).toHaveBeenCalledWith(10, 3);
    expect(wrapper.findAll('.agenda-calendar-sharee')).toHaveLength(1);
    expect(service.getShares).toHaveBeenCalledTimes(2);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-shares-changed');
  });

  it('removes a server share from the server', async () => {
    const wrapper = mountDrawer();
    await open(wrapper);

    await wrapper.find('.agenda-calendar-share-remove-external').trigger('click');
    await flush();

    expect(service.removeExternalShare).toHaveBeenCalledWith(10, 'caldav:1', 'grant-9');
    expect(wrapper.findAll('.agenda-calendar-external-share')).toHaveLength(1);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarShare.externalRemoved(_SERVICE)', 'success');
  });

  it('says so when the calendar is shared with nobody, and only then', async () => {
    service.getShares.mockResolvedValue({shares: [], externalShares: []});
    const wrapper = mountDrawer();
    await open(wrapper);
    expect(wrapper.find('.agenda-calendar-share-none').exists()).toBe(true);
    expect(wrapper.find('.agenda-calendar-share-sharees-title').exists()).toBe(false);
    expect(wrapper.find('.agenda-calendar-share-external-title').exists()).toBe(false);

    // Access held outside eXo alone is still access: the calendar is not
    // "shared with nobody"
    service.getShares.mockResolvedValue({shares: [], externalShares: [OUTSIDE]});
    await open(wrapper);
    expect(wrapper.find('.agenda-calendar-share-none').exists()).toBe(false);
    expect(wrapper.find('.agenda-calendar-share-external-title').exists()).toBe(true);

    service.getShares.mockResolvedValue({shares: [ALICE], externalShares: []});
    await open(wrapper);
    expect(wrapper.find('.agenda-calendar-share-none').exists()).toBe(false);
    expect(wrapper.find('.agenda-calendar-share-sharees-title').exists()).toBe(true);
    expect(wrapper.find('.agenda-calendar-share-external-title').exists()).toBe(false);
  });

});
