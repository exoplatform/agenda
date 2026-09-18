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
import fs from 'fs';
import path from 'path';
import Vue from 'vue';
import {shallowMount} from '@vue/test-utils';

import AgendaLeftPanelRemoteCalendars from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelRemoteCalendars.vue';

/**
 * The lock of a calendar shared from outside this eXo names whoever shared it
 * (EXO-90350): "Shared by Marie Dupont — read-only", the name being the one
 * the calendar server has for the owner, which the connector already sends on
 * the row. Its fallbacks are the connector's own — the address the server
 * spells the principal with when it publishes no display name — so what is
 * pinned here is that the panel renders whatever name arrived, as text, and
 * that a row carrying no name at all keeps the generic sentence.
 *
 * Which assertions carry the mutation evidence, and which only pin the
 * behaviour going forward: the two named cases and the bundle case fail
 * against the code before EXO-90350, because the lock then reads the generic
 * key. The two cases that expect the generic sentence fail against it as
 * well, but for a reason of their own — `agenda-remote-calendar-read-only` is
 * a handle this change adds, so the lookup finds nothing rather than finding
 * the wrong words. They are guards on what must not drift, not proof that the
 * branch was ever wrong.
 */
describe('A calendar shared from outside this eXo names its owner in the lock', () => {

  Vue.config.ignoredElements = [/^v-/, 'exo-user-avatar'];

  /**
   * A share the server reported whose owner the deployment matched to nobody:
   * a display name, no eXo login. The lock's case.
   */
  const NAMED = {id: '/dav/pal/marie/cal/', name: 'Marie', shared: true, readOnly: true, ownerDisplayName: 'Marie Dupont'};

  /** The same, named by the address the server spells its principal with. */
  const ADDRESSED = {id: '/dav/pal/luc/cal/', name: 'Luc', shared: true, readOnly: true, ownerDisplayName: 'luc@stalwart.local'};

  /** The same, with nobody the server would name. */
  const NAMELESS = {id: '/dav/pal/anon/cal/', name: 'Anon', shared: true, readOnly: true};

  /**
   * A read-only calendar of the user's own, which a connector named an owner
   * on all the same. Unreachable through the CalDAV connector, whose contract
   * leaves the owner fields null unless the calendar is shared: this is the
   * guard on that contract, so that a connector naming an owner on a calendar
   * that is nobody's share cannot turn its lock into "Shared by …".
   */
  const OWN_READ_ONLY = {id: '/dav/cal/mine/', name: 'Mine', shared: false, readOnly: true, ownerDisplayName: 'Me Myself'};

  const bundle = fs.readFileSync(path.resolve(__dirname, '../../main/resources/locale/portlet/Agenda_en.properties'), 'utf8');

  let service;

  /**
   * Waits for every pending promise callback and the gathered retrieval's
   * timer, with real timers.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the section over one connected CalDAV connector answering the
   * given listing.
   *
   * @param {Array} calendars what the connector lists
   * @returns {Promise<Object>} the wrapper
   */
  async function mountWith(calendars) {
    const connector = {
      name: 'agenda.caldavCalendar',
      isCaldav: true,
      canListCalendars: true,
      connected: true,
      listCalendars: () => Promise.resolve(calendars),
      hideCalendar: () => Promise.resolve(),
    };
    const wrapper = shallowMount(AgendaLeftPanelRemoteCalendars, {
      attachTo: document.body,
      propsData: {connectors: [connector]},
      mocks: {
        $t: (key, args) => args && `${key}(${Object.values(args).join('|')})` || key,
        $calendarShareService: service,
        $remoteEventConnector: {excludeMirrorCalendar: (one, listed) => Promise.resolve(listed)},
      },
      stubs: {
        'v-menu': {name: 'VMenuStub', props: ['value'], template: '<div class="v-menu-stub"><slot /></div>'},
      },
    });
    await flush();
    await flush();
    return wrapper;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
  });

  beforeEach(() => {
    localStorage.clear();
    service = {
      getSharedWithMe: jest.fn().mockResolvedValue([]),
      setHidden: jest.fn().mockResolvedValue(),
    };
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('names the owner the server gave, and says read-only in the same breath', async () => {
    const wrapper = await mountWith([NAMED]);

    const lock = wrapper.find('.agenda-remote-calendar-read-only');
    expect(lock.exists()).toBe(true);
    expect(lock.attributes('title')).toBe('agenda.leftPanel.sharedByReadOnly(Marie Dupont)');
    expect(lock.attributes('aria-label')).toBe('agenda.leftPanel.sharedByReadOnly(Marie Dupont)');
    expect(lock.attributes('title')).not.toBe('agenda.leftPanel.readOnlyCalendar');
  });

  it('names the owner by the address when that is all the server published', async () => {
    const wrapper = await mountWith([ADDRESSED]);

    expect(wrapper.find('.agenda-remote-calendar-read-only').attributes('title'))
      .toBe('agenda.leftPanel.sharedByReadOnly(luc@stalwart.local)');
  });

  it('keeps the generic sentence when the server named nobody at all', async () => {
    const wrapper = await mountWith([NAMELESS]);

    expect(wrapper.find('.agenda-remote-calendar-read-only').attributes('title'))
      .toBe('agenda.leftPanel.readOnlyCalendar');
  });

  it('keeps the generic sentence on a read-only calendar of the user\'s own, named owner or not', async () => {
    const wrapper = await mountWith([OWN_READ_ONLY]);

    expect(wrapper.find('.agenda-remote-calendar-read-only').attributes('title'))
      .toBe('agenda.leftPanel.readOnlyCalendar');
  });

  it('renders an outside server\'s name as text, never as markup', async () => {
    const injected = '<img src=x onerror="window.__owned = true"> Marie';
    const wrapper = await mountWith([Object.assign({}, NAMED, {ownerDisplayName: injected})]);

    const lock = wrapper.find('.agenda-remote-calendar-read-only');
    // The name travels in an attribute: the characters the server sent, whole,
    // and no element built from them anywhere in the row. What kills a switch
    // to `v-html` is the absence of an element, not the absence of the script
    // it would have run: jsdom fetches nothing, so the injected `onerror`
    // never fires whether the markup was parsed or not, and asking after its
    // effect would pass over a page that had just built the image.
    expect(lock.attributes('title')).toBe(`agenda.leftPanel.sharedByReadOnly(${injected})`);
    expect(lock.element.innerHTML).not.toContain('<img');
    expect(lock.element.querySelector('img')).toBeNull();
    expect(document.body.querySelector('img')).toBeNull();
  });

  it('ships the sentence in the English bundle, with the owner and the read-only part', () => {
    expect(bundle).toContain('agenda.leftPanel.sharedByReadOnly=Shared by {0} \\u2014 read-only\n');
  });
});
