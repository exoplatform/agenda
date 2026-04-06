<template>
  <exo-drawer
    ref="customRecurrentEventDrawer"
    right
    body-classes="hide-scroll"
    class="customRecurrentEventDrawer"
    @closed="$emit('cancel')">
    <template slot="title">
      {{ $t('agenda.title.addEventRecurrent') }}
    </template>
    <template slot="content">
      <form
        ref="form"
        class="ma-5">
        <div class="d-flex flex-row">
          <label class="text-subtitle-1 flex-shrink-0 my-auto">
            {{ $t('agenda.label.repeatEvery') }}
          </label>
          <v-text-field
            v-model="eventRecurrence.interval"
            type="Number"
            min="1"
            name="recurrenceInterval"
            class="mx-4 flex-shrink-1 pt-0"
            dense
            outlined
            required />
          <v-select
            v-model="eventRecurrence.frequency"
            :items="frequencies"
            item-text="label"
            item-value="value"
            class="flex-grow-0 pt-0"
            outlined
            dense
            hide-details />
        </div>
        <div
          v-if="eventRecurrence.frequency === 'WEEKLY'"
          class="d-flex flex-column mt-4">
          <label class="float-left text-subtitle-1 d-none d-md-inline">
            {{ $t('agenda.label.repeatOn') }}
          </label>
          <v-chip-group
            v-model="eventRecurrence.byDay"
            :show-arrows="false"
            next-icon=""
            prev-icon=""
            active-class="primary white--text"
            class="mx-auto d-block no-max-width"
            multiple
            mandatory>
            <v-chip
              v-for="day in days"
              :key="day.value"
              :value="day.value"
              class="me-1">
              <span class="text-uppercase">
                {{ day.text }}
              </span>
            </v-chip>
          </v-chip-group>
        </div>

        <div class="control-group">
          <div class="d-flex flex-column mt-3">
            <div class="control-label text-subtitle-1">{{ $t('agenda.label.endRepeat') }}:</div>
            <div class="controls ms-5">
              <v-radio-group v-model="recurrentEventDate">
                <v-radio
                  :label="$t('agenda.label.never')"
                  value="never"
                  class="py-2" />
                <v-radio value="count">
                  <template slot="label">
                    <div class="d-flex flex-row align-center">
                      <div class="flex-grow-0">
                        {{ $t('agenda.label.after') }}
                      </div>
                      <div class="ps-5 pe-2">
                        <v-text-field
                          v-model="eventRecurrence.count"
                          :disabled="recurrentEventDate !== 'count'"
                          :class="{'background-grey-primary': recurrentEventDate !== 'count'}"
                          type="number"
                          min="1"
                          class="mx-3 pt-0 flex-shrink-1"
                          outlined
                          dense
                          hide-details
                          required />
                      </div>
                      <div class="ps-0">
                        {{ $t('agenda.label.events') }}
                      </div>
                    </div>
                  </template>
                </v-radio>
                <v-radio value="date">
                  <template slot="label">
                    <div class="d-flex flex-row align-center">
                      <div class="flex-grow-0">
                        {{ $t('agenda.label.untilDate') }}
                      </div>
                      <div class="ps-5 pe-2">
                        <date-picker
                          v-model="untilDate"
                          class="background-grey-primary"
                          :disabled="recurrentEventDate !== 'date'" />
                      </div>
                    </div>
                  </template>
                </v-radio>
              </v-radio-group>
            </div>
          </div>
        </div>
      </form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn ms-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          class="btn btn-primary ms-2"
          @click="apply">
          {{ $t('agenda.button.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data() {
    return {
      eventRecurrence: {},
      recurrentEventDate: 'never',
      untilDate: null,
    };
  },
  computed: {
    eventRecurrenceByDay() {
      return this.eventRecurrence && this.eventRecurrence.byDay;
    },
    frequencies() {
      return [
        { label: this.$t('agenda.day'),   value: 'DAILY'   },
        { label: this.$t('agenda.week'),  value: 'WEEKLY'  },
        { label: this.$t('agenda.month'), value: 'MONTHLY' },
        { label: this.$t('agenda.year'),  value: 'YEARLY'  },
      ];
    },
    days() {
      return [{
        text: this.getWeekDayLabel(0),
        value: 'SU'
      },{
        text: this.getWeekDayLabel(1),
        value: 'MO'
      },{
        text: this.getWeekDayLabel(2),
        value: 'TU'
      },{
        text: this.getWeekDayLabel(3),
        value: 'WE'
      },{
        text: this.getWeekDayLabel(4),
        value: 'TH'
      },{
        text: this.getWeekDayLabel(5),
        value: 'FR'
      },{
        text: this.getWeekDayLabel(6),
        value: 'SA'
      }];
    },
  },
  watch: {
    eventRecurrenceByDay() {
      this.$root.$forceUpdate();
    },
    recurrentEventDate() {
      if (this.recurrentEventDate === 'date') {
        this.eventRecurrence.count = '';
      } else if (this.recurrentEventDate === 'count') {
        this.eventRecurrence.count = 1;
        this.eventRecurrence.until = null;
      } else {
        this.eventRecurrence.until = null;
        this.eventRecurrence.count = '';
      }
    },
  },
  methods: {
    getWeekDayLabel(weekDayNumber) {
      const date = new Date(`2020-11-0${weekDayNumber + 1}`);
      const dayName = date.toLocaleDateString(eXo.env.portal.language, { weekday: 'short' });
      return dayName.length > 3 ? dayName.substring(0, 3) : dayName;
    },
    apply() {
      if (!this.$refs.form.reportValidity()) {
        return;
      }
      if (this.recurrentEventDate === 'date') {
        const endDate = new Date(this.untilDate);
        endDate.setHours(23, 59, 59, 999);
        this.eventRecurrence.until = this.$agendaUtils.toRFC3339(endDate);
        this.eventRecurrence.count = '';
      } else if (this.recurrentEventDate === 'count') {
        this.eventRecurrence.until = null;
      } else {
        this.eventRecurrence.until = null;
        this.eventRecurrence.count = '';
      }
      this.$emit('apply', this.eventRecurrence);
      this.$refs.customRecurrentEventDrawer.close();
    },
    close() {
      this.$refs.customRecurrentEventDrawer.close();
    },
    open(eventRecurrence) {
      this.eventRecurrence = eventRecurrence || {};
      if (this.eventRecurrence.until) {
        this.recurrentEventDate = 'date';
        this.untilDate = this.$agendaUtils.toDate(this.eventRecurrence.until);
        this.eventRecurrence.count = null;
      } else if (this.eventRecurrence.count > 0) {
        this.recurrentEventDate = 'count';
        if (!this.eventRecurrence.count) {
          this.eventRecurrence.count = 1;
        }
        this.eventRecurrence.until = null;
      } else {
        this.recurrentEventDate = 'never';
        this.eventRecurrence.until = null;
        this.eventRecurrence.count = null;
      }
      this.$refs.customRecurrentEventDrawer.open();
    },
  }
};
</script>
