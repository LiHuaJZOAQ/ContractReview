import { config } from '@vue/test-utils'

function slotStub(tag, withHeader = false) {
  if (withHeader) {
    return { template: `<${tag} class="${tag}-stub"><slot name="header" /><slot /></${tag}>` }
  }
  return { template: `<${tag} class="${tag}-stub"><slot /></${tag}>` }
}

config.global.stubs = {
  'el-button': slotStub('button'),
  'el-input': { name: 'ElInputStub', template: '<input class="el-input-stub" />' },
  'el-form': slotStub('form'),
  'el-form-item': slotStub('div'),
  'el-card': slotStub('div', true),
  'el-tag': slotStub('span'),
  'el-table': slotStub('div'),
  'el-table-column': { template: '<div class="el-table-column-stub" />' },
  'el-pagination': { template: '<div class="el-pagination-stub" />' },
  'el-progress': { template: '<div class="el-progress-stub" />' },
  'el-tabs': slotStub('div'),
  'el-tab-pane': { name: 'ElTabPaneStub', template: '<div class="el-tab-pane-stub">{{ $attrs.label }}</div>' },
  'el-upload': slotStub('div', true),
  'el-switch': { template: '<div class="el-switch-stub" />' },
  'el-divider': { template: '<div class="el-divider-stub" />' },
  'el-alert': slotStub('div'),
  'el-descriptions': slotStub('div'),
  'el-descriptions-item': slotStub('div'),
  'el-empty': slotStub('div'),
  'el-icon': slotStub('span'),
  'el-menu': slotStub('div'),
  'el-menu-item': slotStub('div'),
  'el-sub-menu': slotStub('div'),
  'el-header': slotStub('div'),
  'el-main': slotStub('div'),
  'el-container': slotStub('div'),
  'el-popover': { template: '<div class="el-popover-stub"><slot /><slot name="reference" /></div>' },
  'el-dropdown': slotStub('div'),
  'el-dropdown-menu': slotStub('div'),
  'el-dropdown-item': slotStub('div'),
  'router-link': { template: '<a><slot /></a>' },
}

config.global.mocks = {
  $route: { params: { taskId: '99' }, query: {} },
  $router: { push: vi.fn() },
}

config.global.directives = {
  loading: {
    mounted() {},
    updated() {},
    beforeUnmount() {},
  },
}
