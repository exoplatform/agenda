<template>
  <exo-drawer
    ref="personalCalendarDrawer"
    :loading="saving"
    :autofocus="false"
    class="agendaPersonalCalendarDrawer"
    right
    @opened="focusName">
    <template slot="title">
      {{ title }}
    </template>
    <template slot="content">
      <form
        class="mx-4 mt-4"
        @submit.stop.prevent="save">
        <v-label for="agendaPersonalCalendarName">
          {{ $t('agenda.calendar.name') }}
        </v-label>
        <v-text-field
          id="agendaPersonalCalendarName"
          ref="nameInput"
          v-model="calendar.name"
          :placeholder="$t('agenda.calendar.namePlaceholder')"
          :aria-label="$t('agenda.calendar.name')"
          :error-messages="nameErrorMessages"
          class="mt-2 mb-5 pt-0"
          type="text"
          maxlength="200"
          outlined
          dense
          @input="nameErrorKey = null" />
        <v-label for="agendaPersonalCalendarDescription">
          {{ $t('agenda.calendar.description') }}
        </v-label>
        <!--
          The platform skin borders the native textarea element itself, so a
          Vuetify `outlined` textarea renders two nested borders: description
          fields in drawers use the bare skin-styled textarea instead (cf.
          social's GroupsManagementFormDrawer)
        -->
        <div class="d-flex mt-2 mb-5">
          <textarea
            id="agendaPersonalCalendarDescription"
            v-model="calendar.description"
            :placeholder="$t('agenda.calendar.descriptionPlaceholder')"
            :aria-label="$t('agenda.calendar.description')"
            class="ignore-vuetify-classes flex-grow-1 textarea-no-resize"
            rows="3"
            maxlength="2000"></textarea>
        </div>
        <v-label>
          {{ $t('agenda.calendar.color') }}
        </v-label>
        <div class="d-flex align-center mt-2">
          <v-menu
            ref="colorMenu"
            v-model="colorMenu"
            :close-on-content-click="false"
            bottom
            left>
            <template #activator="{ on, attrs }">
              <div
                class="d-flex align-center"
                v-bind="attrs"
                v-on="on">
                <v-card
                  v-if="calendar.color"
                  :color="calendar.color"
                  height="24"
                  width="24"
                  flat />
                <v-card
                  v-else
                  class="d-flex align-center justify-center"
                  height="24"
                  width="24"
                  outlined>
                  <v-icon size="14" class="text-light-color">fa-palette</v-icon>
                </v-card>
                <div class="ms-2">{{ calendar.color || $t('agenda.calendar.colorAuto') }}</div>
              </div>
            </template>
            <v-card>
              <v-color-picker
                v-model="newColor"
                :swatches="$agendaUtils.EVENT_COLOR_SWATCHES"
                class="ma-2"
                mode="hexa"
                show-swatches
                flat />
              <v-card-actions>
                <v-spacer />
                <v-btn class="btn ms-2" @click="colorMenu = false">
                  {{ $t('agenda.button.cancel') }}
                </v-btn>
                <v-btn class="btn btn-primary ms-2" @click="applyColor">
                  {{ $t('agenda.button.apply') }}
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-menu>
        </div>
      </form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="!canSave"
          :loading="saving"
          class="btn btn-primary"
          @click="save">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    // The full calendar object being edited, kept aside so the PUT payload
    // carries every stored field, not only the ones this form manages
    sourceCalendar: null,
    calendar: {
      name: '',
      description: '',
      color: null,
    },
    colorMenu: false,
    newColor: null,
    nameErrorKey: null,
    saving: false,
  }),
  computed: {
    /**
     * Whether the drawer edits an existing calendar, as opposed to creating a
     * new one. Decides the title and whether the save is a PUT or a POST.
     *
     * @returns {Boolean} true when editing an existing calendar
     */
    isEdit() {
      return !!(this.sourceCalendar && this.sourceCalendar.id);
    },
    /**
     * The drawer title: 'Add a calendar' when creating, 'Edit calendar' when
     * opened from a calendar's action menu.
     *
     * @returns {String} localized drawer title
     */
    title() {
      return this.isEdit ? this.$t('agenda.calendar.editCalendar') : this.$t('agenda.calendar.addCalendar');
    },
    /**
     * The identity id of the current user, owner of every calendar managed
     * through this drawer.
     *
     * @returns {Number} current user identity id
     */
    userIdentityId() {
      return Number(eXo.env.portal.userIdentityId);
    },
    /**
     * The validation message displayed under the name field when the server
     * refused the previous save (duplicate or too long name).
     *
     * @returns {Array} error messages for the name field, empty when valid
     */
    nameErrorMessages() {
      return this.nameErrorKey ? [this.$t(this.nameErrorKey)] : [];
    },
    /**
     * Whether the save button is active. A user calendar needs a non-blank
     * name — two unnamed calendars would be indistinguishable — but the
     * default (system) calendar may be saved with an empty name, which
     * restores its localized 'My calendar' label.
     *
     * @returns {Boolean} true when the form can be saved
     */
    canSave() {
      if (this.saving) {
        return false;
      }
      const name = this.calendar.name && this.calendar.name.trim();
      return !!name || !!(this.sourceCalendar && this.sourceCalendar.system);
    },
  },
  watch: {
    /**
     * Seeds the picker with the current color — or the first palette swatch
     * when none is chosen yet — each time the color menu opens, so cancelling
     * the menu never leaks a half-picked color into the form.
     *
     * @param {Boolean} opened true when the color menu just opened
     * @returns {void}
     */
    colorMenu(opened) {
      if (opened) {
        this.newColor = this.calendar.color || this.$agendaUtils.EVENT_COLOR_SWATCHES[0][0];
      }
    },
  },
  created() {
    this.$root.$on('agenda-personal-calendar-drawer-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-personal-calendar-drawer-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer, empty for a creation or pre-filled with the calendar
     * to edit.
     *
     * @param {Object} calendar the calendar to edit, or null to create one
     * @returns {void}
     */
    open(calendar) {
      this.sourceCalendar = calendar || null;
      this.calendar = {
        name: calendar && calendar.name || '',
        description: calendar && calendar.description || '',
        // No color chosen at creation means 'automatic': the server assigns
        // the first palette color not already used by a sibling calendar
        color: calendar && calendar.color || null,
      };
      this.nameErrorKey = null;
      this.colorMenu = false;
      this.$refs.personalCalendarDrawer.open();
    },
    /**
     * Closes the drawer discarding any pending change.
     * @returns {void}
     */
    close() {
      this.$refs.personalCalendarDrawer.close();
    },
    /**
     * Puts the caret in the name field once the drawer is open. exo-drawer's
     * own autofocus is disabled: it focuses the drawer container 50ms after
     * opening, which would steal the focus from the field.
     * @returns {void}
     */
    focusName() {
      this.$nextTick().then(() => window.setTimeout(() => {
        if (this.$refs.nameInput) {
          this.$refs.nameInput.focus();
        }
      }, 200));
    },
    /**
     * Applies the color chosen in the picker to the form and closes the
     * picker menu. The color is only sent to the server on save.
     * @returns {void}
     */
    applyColor() {
      this.calendar.color = this.newColor;
      this.colorMenu = false;
    },
    /**
     * Saves the calendar: a POST creating it or a PUT updating the edited
     * one, then refreshes the calendar list — and the displayed events on an
     * edit, since the name labels them and the color paints them. Server
     * validation errors on the name surface under the name field.
     * @returns {void}
     */
    save() {
      if (!this.canSave) {
        return;
      }
      const name = this.calendar.name && this.calendar.name.trim() || '';
      const description = this.calendar.description && this.calendar.description.trim() || '';
      let calendarToSave;
      if (this.isEdit) {
        calendarToSave = Object.assign({}, this.sourceCalendar, {
          name: name,
          description: description,
          color: this.calendar.color || this.sourceCalendar.color,
        });
      } else {
        calendarToSave = {
          name: name,
          description: description,
          owner: {
            id: String(this.userIdentityId),
          },
        };
        if (this.calendar.color) {
          calendarToSave.color = this.calendar.color;
        }
      }
      this.saving = true;
      this.$calendarService.saveCalendar(calendarToSave)
        .then(() => {
          this.$root.$emit('agenda-refresh-personal-calendars');
          if (this.isEdit) {
            this.$root.$emit('agenda-refresh');
          }
          this.close();
        })
        .catch(error => this.handleSaveError(error))
        .finally(() => this.saving = false);
    },
    /**
     * Maps a calendar save error to what the user sees: name validation
     * errors (duplicate name, name too long) are displayed under the name
     * field, anything else falls back to a generic alert.
     *
     * @param {Error} error error thrown by the calendar service
     * @returns {void}
     */
    handleSaveError(error) {
      const errorMessage = String(error && error.message || '');
      if (errorMessage.indexOf('agenda.calendarNameAlreadyExists') >= 0) {
        this.nameErrorKey = 'agenda.calendarNameAlreadyExists';
      } else if (errorMessage.indexOf('agenda.calendarNameExceedsMaxLength') >= 0) {
        this.nameErrorKey = 'agenda.calendarNameExceedsMaxLength';
      } else {
        this.$root.$emit('alert-message', this.$t('agenda.calendarSave.error'), 'error');
      }
    },
  },
};
</script>
