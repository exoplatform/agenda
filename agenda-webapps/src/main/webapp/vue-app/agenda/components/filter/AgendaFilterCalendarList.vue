<template>
  <v-list>
    <v-list-item class="agenda-calendar-settings px-0">
      <v-list-item-action class="mr-2">
        <v-checkbox
          v-model="selectAll"
          :indeterminate="partiallySelected"
          color="primary"
          class="agenda-calendar-settings-color ma-auto"
          @click="changeAllSelection" />
      </v-list-item-action>
      <v-list-item-content>
        <agenda-filter-calendar-search
          ref="queryInput"
          v-model="query"
          class="mb-0 mt-1" />
      </v-list-item-content>
      <v-list-item-action class="ml-2">
        <select
          v-model="selectionType"
          class="width-auto my-auto subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
          <option value="all">{{ $t('agenda.all') }}</option>
          <option value="selected">{{ $t('agenda.selected') }}</option>
          <option value="nonSelected">{{ $t('agenda.nonSelected') }}</option>
        </select>
      </v-list-item-action>
    </v-list-item>
    <agenda-filter-calendar-item
      v-for="calendar in filteredCalendars"
      :key="calendar.owner.id"
      :calendar="calendar"
      :owner-ids="spaceIdentityIds"
      :selected-owner-ids="value"
      @changeSelection="changeSelection" />

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
    calendars: [],
    selectionType: 'all',
    selectAll: true,
    loading: false,
    query: null,
    limit: 20,
    pageSize: 20,
    totalSize: 0,
  }),
  computed: {
    filteredCalendars() {
      if (this.selectionType === 'selected') {
        return this.selectedCalendars;
      } else if (this.selectionType === 'nonSelected') {
        return this.unselectedCalendars;
      }
      return this.calendars;
    },
    partiallySelected() {
      return this.value && this.value.length && (this.hasMore || this.spaceIdentityIds.length !== this.value.length);
    },
    hasMore() {
      return this.limit < this.totalSize;
    },
    spaceIdentityIds() {
      return this.spaces && this.spaces.filter(space => space.identity && space.identity.id).map(space => Number(space.identity.id)) || [];
    },
    selectedCalendars() {
      if (this.value === false) {
        return [];
      }
      if (this.selectAll || !this.value || !this.value.length) {
        return this.calendars;
      }
      return this.calendars && this.calendars.filter(calendar => this.value.indexOf(Number(calendar.owner.id)) >= 0) || [];
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
      if (!this.calendars || !this.calendars.length) {
        this.retrieveCalendars();
      }
      this.selectAll = !this.value || !this.value.length;
      window.setTimeout(() => this.$refs.queryInput.$el.querySelector('input').focus(), 200);
    },
    loadMore() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
    changeAllSelection() {
      if (this.selectAll) {
        this.checkAll();
      } else {
        this.uncheckAll();
      }
    },
    changeSelection(selectedOwnerIds) {
      if (this.value && this.value.length === 1 && !selectedOwnerIds.length) {
        this.uncheckAll();
      } else if (!this.query && selectedOwnerIds.length === this.spaceIdentityIds.length) {
        this.checkAll();
      } else {
        this.value = selectedOwnerIds;
        this.selectAll = false;
        this.$emit('input', this.value);
      }
    },
    checkAll() {
      if (this.query) {
        this.value = this.spaceIdentityIds.slice();
        this.selectAll = false;
      } else {
        this.value = [];
        this.selectAll = true;
      }
      this.$emit('input', this.value);
    },
    uncheckAll() {
      this.value = false;
      this.selectAll = false;
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
      }).then(data => {
        if (data && data.calendars) {
          this.calendars = data.calendars;
        } else {
          this.calendars = [];
        }

        const forceLoad = this.limit === this.pageSize || this.query;
        if (this.value === false) {
          this.uncheckAll();
        } else if (this.value && this.value.length && forceLoad) {
          if (this.selectedCalendars.length < this.value.length) {
            return this.$calendarService.getCalendars(0, 0, false, this.value);
          }
        } else if (!this.value || !this.value.length) {
          this.selectedCalendars = this.calendars.slice();
        }
      }).then(data => {
        if (data && data.calendars) {
          if (this.query) {
            this.selectedCalendars = data.calendars.filter(cal => cal.owner && cal.owner.space && cal.owner.space.displayName.indexOf(this.query) >= 0);
          } else {
            this.selectedCalendars = data.calendars;
          }
        }
      }).finally(() => this.loading = false);
    },
  },
};
</script>