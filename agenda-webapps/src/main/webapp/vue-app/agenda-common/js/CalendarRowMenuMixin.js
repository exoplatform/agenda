/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

/**
 * The class of a calendar row menu's content, the part a press does not close.
 */
export const ROW_MENU_CONTENT_CLASS = 'agendaCalendarRowMenu';

/**
 * Closes the ⋮ menus of the agenda's calendar rows the way the platform's own
 * menus close: on any press outside them, and when another one opens.
 *
 * <p><b>Why Vuetify does not do it here.</b> The Vuetify the platform ships
 * (2.3.10, social's js/lib/vuetify.min.js) registers its click-outside listener
 * on the first [data-app] of the page — (document.querySelector("[data-app]")
 * || document.body).addEventListener("click", …, true). On an eXo page that
 * first [data-app] is #vuetify-apps, where the menu content is moved, while the
 * calendar rows and the grid live in #AgendaApplication, another [data-app]
 * that is not inside it: a click on the grid never reaches the listener, so the
 * menu stays open and several end up open at once.
 *
 * <p><b>The pattern followed, with one difference from social's own.</b> The menu
 * is driven by the component's state and a listener sits on the whole document
 * while a menu is open, as SpaceHamburgerActionMenu and ActivityHeadMenu do —
 * but on `click`, not `mousedown`: every consumer here opens through a real
 * Vuetify activator (`#activator="{ on, attrs }"`, never `open-on-hover`), and
 * `Activatable.genActivatorListeners()` stops propagation in its click branch
 * before toggling `isActive`, so the activator's own click never reaches this
 * document listener — no race with the press that opened the menu, and no
 * delay needed to outlast one (frontend-vue.md, "the menu has a Vuetify
 * activator opened on click"). Two additions, both needed by lists of rows: a
 * press inside the menu's own content does not close it (so an entry held down
 * a little longer still gets its click), and opening a menu tells every other
 * calendar menu of the app to close (one root event, whichever list or row
 * holds the other menu).
 *
 * <p>Escape and choosing an entry still close the menu through Vuetify's own
 * handling, which updates the state through the menu's input event.
 *
 * <p>Usage, on a v-menu of a row: :value="isRowMenuOpen(key)",
 * @input="toggleRowMenu(key, $event)" and content-class="agendaCalendarRowMenu".
 */
export default {
  data: () => ({
    openRowMenu: null,
  }),
  watch: {
    /**
     * Listens for presses anywhere on the page while a menu is open, and tells
     * the other calendar menus that this one opened.
     *
     * @param {String|Number} current the key of the open menu, null for none
     * @param {String|Number} previous the key of the menu open before
     * @returns {void}
     */
    openRowMenu(current, previous) {
      if (current === null) {
        document.removeEventListener('click', this.closeRowMenuOnOutsidePress);
        return;
      }
      if (previous === null) {
        document.addEventListener('click', this.closeRowMenuOnOutsidePress);
      }
      this.$root.$emit('agenda-calendar-row-menu-opened', this._uid);
    },
  },
  created() {
    this.$root.$on('agenda-calendar-row-menu-opened', this.closeRowMenuOpenedElsewhere);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-row-menu-opened', this.closeRowMenuOpenedElsewhere);
    document.removeEventListener('click', this.closeRowMenuOnOutsidePress);
  },
  methods: {
    /**
     * Whether a row's menu is the open one.
     *
     * @param {String|Number} key the row's menu key
     * @returns {Boolean} true when open
     */
    isRowMenuOpen(key) {
      return this.openRowMenu !== null && this.openRowMenu === key;
    },
    /**
     * Follows a menu's own opening and closing: its activator, Escape, an entry
     * chosen.
     *
     * @param {String|Number} key the row's menu key
     * @param {Boolean} open whether the menu opened
     * @returns {void}
     */
    toggleRowMenu(key, open) {
      if (open) {
        this.openRowMenu = key;
      } else if (this.openRowMenu === key) {
        this.openRowMenu = null;
      }
    },
    /**
     * Closes the open menu on a click anywhere outside its content — the
     * activator's own click never reaches here, so no delay is needed to
     * outlast the press that opened the menu.
     *
     * @param {Event} event the click
     * @returns {void}
     */
    closeRowMenuOnOutsidePress(event) {
      const target = event && event.target;
      if (target && target.closest && target.closest(`.${ROW_MENU_CONTENT_CLASS}`)) {
        return;
      }
      this.openRowMenu = null;
    },
    /**
     * Closes this component's menu when a calendar menu opened in another one.
     *
     * @param {Number} openerUid the Vue identifier of the component that opened
     *          a menu
     * @returns {void}
     */
    closeRowMenuOpenedElsewhere(openerUid) {
      if (openerUid !== this._uid) {
        this.openRowMenu = null;
      }
    },
  },
};
