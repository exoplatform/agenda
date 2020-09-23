<template>
  <v-list-item class="agenda-calendar-settings px-0">
    <v-list-item-action class="flex-grow-1">
      <v-checkbox
        v-model="checked"
        :color="calendarColor"
        :label="calendarDisplayName"
        class="agenda-calendar-settings-color ma-auto"
        @click="changeSelection" />
    </v-list-item-action>
    <v-list-item-action :id="calendarMenuId" class="calendarSettingActions">
      <v-menu
        v-if="canEditCalendar"
        ref="menu"
        v-model="menu"
        :close-on-content-click="false"
        :content-class="calendarMenuId"
        bottom
        left>
        <template v-slot:activator="{ on, attrs }">
          <v-btn
            icon
            v-bind="attrs"
            v-on="on">
            <v-icon>mdi-dots-vertical</v-icon>
          </v-btn>
        </template>
        <v-card>
          <v-color-picker
            v-model="selectedCalendarColor"
            class="ma-2"
            hide-inputs
            flat />
          <v-card-actions>
            <v-spacer />
            <v-btn
              :disabled="saving"
              class="btn ml-2"
              @click="closeMenu">
              {{ $t('agenda.button.cancel') }}
            </v-btn>
            <v-btn
              :loading="saving"
              :disabled="saving"
              class="btn btn-primary ml-2"
              @click="applyColor">
              {{ $t('agenda.button.apply') }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-menu>
    </v-list-item-action>
  </v-list-item>
</template>

<script>
export default {
  props: {
    calendar: {
      type: Object,
      default: null,
    },
    ownerIds: {
      type: Array,
      default: () => [],
    },
    selectedOwnerIds: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    selectedCalendarColor: null,
    saving: false,
    menu: false,
    checked: false,
  }),
  computed: {
    selected() {
      return this.selectedOwnerIds !== false && (!this.selectedOwnerIds.length || this.selectedOwnerIds.find(ownerId => ownerId === this.calendarOwnerId));
    },
    calendarOwnerId() {
      return Number(this.calendar.owner.id);
    },
    calendarMenuId() {
      return `settingsMenu${this.calendarOwnerId}`;
    },
    calendarColor() {
      return this.calendar.color;
    },
    canEditCalendar() {
      return this.calendar && this.calendar.acl && this.calendar.acl.canEdit;
    },
    calendarDisplayName() {
      const owner = this.calendar.owner;
      const profile = owner.space || owner.profile;
      return profile.displayName || profile.fullname || profile.fullName;
    },
  },
  watch: {
    selected() {
      this.checked = this.selected;
    },
  },
  created() {
    this.selectedCalendarColor = this.calendar.color;
    // Force to close other DatePicker menus when opening a new one 
    $('.calendarSettingActions button').on('click', (e) => {
      if (e.target && !$(e.target).parents(`#${this.calendarMenuId}`).length) {
        this.menu = false;
      }
    });

    // Force to close DatePickers when clicking outside
    $(document).on('click', (e) => {
      if (e.target && !$(e.target).parents(`.${this.calendarMenuId}`).length) {
        this.menu = false;
      }
    });
  },
  mounted() {
    this.checked = this.selected;
  },
  methods: {
    changeSelection() {
      if (this.selectedOwnerIds) {
        if (this.selected) {
          if (this.selectedOwnerIds && !this.selectedOwnerIds.length) {
            this.selectedOwnerIds = this.ownerIds.slice();
          }
          const index = this.selectedOwnerIds.findIndex(ownerId => ownerId === this.calendarOwnerId);
          if (index >= 0) {
            this.selectedOwnerIds.splice(index, 1);
          }
        } else {
          this.selectedOwnerIds.push(this.calendarOwnerId);
        }
      } else if (!this.selected) {
        this.selectedOwnerIds = [this.calendarOwnerId];
      }
      this.$emit('changeSelection', this.selectedOwnerIds);
    },
    reset() {
      this.selectedCalendarColor = this.calendar.color;
    },
    applyColor() {
      const calendarToSave = JSON.parse(JSON.stringify(this.calendar));
      calendarToSave.color = this.selectedCalendarColor;
      this.saving = true;
      this.$calendarService.saveCalendar(calendarToSave)
        .then(() => {
          this.calendar.color = this.selectedCalendarColor;
          this.$root.$emit('agenda-calendar-color-changed', this.calendar.id, this.calendarColor);
          this.closeMenu();
        })
        .finally(() => this.saving = false);
    },
    closeMenu() {
      this.menu = false;
    },
  },
};
</script>