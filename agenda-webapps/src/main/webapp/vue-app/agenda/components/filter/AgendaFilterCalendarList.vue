<template>
  <v-list>
    <agenda-filter-calendar-search v-model="query" class="mb-0 mt-1" />
    <template v-if="selectedCalendars && selectedCalendars.length">
      <v-list-item class="agenda-calendar-settings px-0">
        <v-list-item-action>
          {{ $t('agenda.selectedAgendas') }}
        </v-list-item-action>
        <v-list-item-content>
          <v-divider />
        </v-list-item-content>
        <v-list-item-action>
          <v-btn
            text
            color="primary"
            @click="uncheckAll">
            {{ $t('agenda.button.uncheckAll') }}
          </v-btn>
        </v-list-item-action>
      </v-list-item>
      <agenda-filter-calendar-item
        v-for="calendar in selectedCalendars"
        :key="calendar.owner.id"
        :calendar="calendar"
        selected
        @unselect-calendar="unselectCalendar(calendar)" />
    </template>

    <template v-if="unselectedCalendars.length">
      <v-list-item class="agenda-calendar-settings px-0">
        <v-list-item-action>
          {{ $t('agenda.allAgendas') }}
        </v-list-item-action>
        <v-list-item-content>
          <v-divider />
        </v-list-item-content>
        <v-list-item-action>
          <v-btn
            text
            color="primary"
            @click="checkAll">
            {{ $t('agenda.button.checkAll') }}
          </v-btn>
        </v-list-item-action>
      </v-list-item>
      <agenda-filter-calendar-item
        v-for="calendar in unselectedCalendars"
        :key="calendar.owner.id"
        :calendar="calendar"
        :selected="false"
        @select-calendar="selectCalendar(calendar)" />
    </template>

    <v-flex v-if="hasMore" class="agendaLoadMoreParent d-flex my-4 border-box-sizing">
      <v-btn
        :loading="loading"
        :disabled="loading"
        class="btn mx-auto"
        @click="loadMore">
        {{ $t('agenda.button.loadMore') }}
      </v-btn>
    </v-flex>
  </v-list>
</template>

<script>
export default {
  props: {
    value: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    spaces: [],
    selectedCalendars: [],
    calendars: [],
    loading: false,
    query: null,
    limit: 0,
    pageSize: 20,
    totalSize: 0,
  }),
  computed: {
    hasMore() {
      return this.limit < this.totalSize;
    },
    spaceIdentityIds() {
      return this.spaces && this.spaces.filter(space => space.identity && space.identity.id).map(space => Number(space.identity.id)) || [];
    },
    unselectedCalendars() {
      return this.calendars && this.calendars.filter(calendar => !this.selectedCalendars || !this.selectedCalendars.find(cal => cal.owner.id === calendar.owner.id)) || [];
    },
  },
  watch: {
    limit() {
      this.retrieveCalendars();
    },
    query() {
      this.retrieveCalendars();
    },
    loading() {
      if (this.loading) {
        this.$emit('start-loading');
      } else {
        this.$emit('end-loading');
      }
    },
  },
  methods: {
    reset() {
      this.limit = this.pageSize;
    },
    loadMore() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
    checkAll() {
      this.selectedCalendars = this.calendars.slice();
      this.value = [];
      this.$emit('input', this.value);
    },
    uncheckAll() {
      this.selectedCalendars = null;
      this.value = false;
      this.$emit('input', this.value);
    },
    selectCalendar(calendar) {
      if (!this.selectedCalendars) {
        this.selectedCalendars = [];
      }
      this.selectedCalendars.push(calendar);
      this.value = this.selectedCalendars.map(cal => cal.owner.id);
      this.$emit('input', this.value);
    },
    unselectCalendar(calendar) {
      if (this.selectedCalendars && this.selectedCalendars.length <= 1) {
        this.uncheckAll();
      } else if (this.selectedCalendars) {
        const index = this.selectedCalendars.findIndex(cal => cal.owner.id === calendar.owner.id);
        if (index >= 0) {
          this.selectedCalendars.splice(index, 1);
        }
        this.value = this.selectedCalendars.map(cal => cal.owner.id);
      }
      this.$emit('input', this.value);
    },
    retrieveCalendars() {
      this.loading = true;
      this.$spaceService.getSpaces(this.query, 0, this.limit, 'member', 'identity').then(data => {
        this.spaces = data.spaces;
        this.totalSize = data.size || this.totalSize;
        return this.$nextTick();
      }).then(() => {
        if (this.spaceIdentityIds && this.spaceIdentityIds.length) {
          return this.$calendarService.getCalendars(0, this.limit, false, this.spaceIdentityIds);
        }
      })
        .then(data => {
          if (data && data.calendars) {
            this.calendars = data.calendars;
          } else {
            this.calendars = [];
          }

          const forceLoad = this.limit === this.pageSize || this.query;
          if (this.value === false) {
            this.uncheckAll();
          } else if (this.value && this.value.length && forceLoad) {
            this.selectedCalendars = this.calendars.filter(calendar => this.value.indexOf(calendar.owner.id) >= 0);
            if (this.selectedCalendars.length < this.value.length) {
              return this.$calendarService.getCalendars(0, 0, false, this.value);
            }
          } else if (!this.value || !this.value.length) {
            this.selectedCalendars = this.calendars.slice();
          }
        })
        .then(data => {
          if (data && data.calendars) {
            if (this.query) {
              this.selectedCalendars = data.calendars.filter(cal => cal.owner && cal.owner.space && cal.owner.space.displayName.indexOf(this.query) >= 0);
            } else {
              this.selectedCalendars = data.calendars;
            }
          }
        })
        .finally(() => this.loading = false);
    },
  },
};
</script>