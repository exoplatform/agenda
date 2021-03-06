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
        <div class="flex d-flex flex-row">
          <label class="text-subtitle-1 my-auto">
            {{ $t('agenda.label.repeatEvery') }}
          </label>
          <input
            v-model="eventRecurrence.interval"
            type="Number"
            min="1"
            name="recurrenceInterval"
            class="recurrenceInterval ignore-vuetify-classes my-auto recurrence-interval mx-4"
            autofocus
            required>
          <select v-model="eventRecurrence.frequency" class="flex-end ignore-vuetify-classes width-auto my-auto">
            <option value="DAILY">{{ $t('agenda.day') }}</option>
            <option value="WEEKLY">{{ $t('agenda.week') }}</option>
            <option value="MONTHLY">{{ $t('agenda.month') }}</option>
            <option value="YEARLY">{{ $t('agenda.year') }}</option>
          </select>
        </div>

        <div v-if="eventRecurrence.frequency === 'WEEKLY'" class="d-flex flex-column recurrenceDays mt-4">
          <label class="float-left text-subtitle-1 d-none d-md-inline">
            {{ $t('agenda.label.repeatOn') }}
          </label>
          <v-chip-group
            v-model="eventRecurrence.byDay"
            :show-arrows="false"
            next-icon=""
            prev-icon=""
            active-class="primary white--text"
            class="mx-auto"
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
                        <input
                          v-model="eventRecurrence.count"
                          :disabled="recurrentEventDate !== 'count'"
                          type="Number"
                          class="recurrenceCount ignore-vuetify-classes"
                          min="1"
                          required>
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
        this.eventRecurrence.until = this.$agendaUtils.toRFC3339(this.untilDate);
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
