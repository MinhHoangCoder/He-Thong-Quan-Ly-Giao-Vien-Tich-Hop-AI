import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { dashboardApi } from '@/api/dashboard'

/**
 * Số liệu Bảng điều khiển, sống NGOÀI vòng đời component.
 *
 * VÌ SAO CẦN STORE: `App.vue` render `<RouterView/>` trần, không có `<KeepAlive>` — rời trang là
 * Vue huỷ component, quay lại là dựng mới và `onMounted` gọi lại toàn bộ API. Người dùng chuyển
 * qua Bảng lương xem một con số rồi quay về là phải ngồi chờ lại từ đầu. Để dữ liệu ở đây thì nó
 * sống qua mọi lần chuyển trang; component chỉ còn việc vẽ.
 *
 * CÁCH LÀM MỚI — "hiện số cũ, tải ngầm bên dưới": quay lại trang là thấy nội dung NGAY, đồng
 * thời một lượt gọi chạy ngầm và thay số khi về. Không bao giờ phải nhìn màn hình trắng, cũng
 * không bao giờ xem phải số cũ mà không biết.
 *
 * KHOÁ BỘ LỌC: dữ liệu chỉ đúng cho đúng bộ lọc sinh ra nó. Đổi kỳ hay đổi trường là khoá đổi
 * theo, và bộ nhớ đệm của bảng chi tiết bị bỏ — giữ lại thì màn hình ghép số của hai bộ lọc khác
 * nhau, một lỗi rất khó nhận ra vì con số nào trông cũng hợp lý.
 */
export const useDashboardStore = defineStore('dashboard', () => {
  const khoaDuLieu = ref('') // bộ lọc đã sinh ra dữ liệu ĐANG HIỂN THỊ
  const khoaYeuCau = ref('') // bộ lọc của lượt đang tải
  const tomTat = ref(null) // thẻ chỉ số
  const bieuDo = ref(null) // theo tháng + cơ cấu nhóm môn
  const dieuHanh = ref(null) // việc cần xử lý + lịch + phân công
  const bang = ref({}) // bảng chi tiết, đệm theo chiều: { GIAO_VIEN: [...] }
  const danhMuc = ref({ truong: [], nhomMon: [] })

  // Trạng thái giao diện cũng phải sống qua việc rời trang, nếu không quay lại là tab nhảy về
  // "Theo giáo viên" và trang cuộn vọt lên đầu.
  const tab = ref('GIAO_VIEN')
  const viTriCuon = ref(0)

  const tai = reactive({ tomTat: false, bieuDo: false, dieuHanh: false, bang: false })
  const loi = ref('')

  const dangTai = computed(() => tai.tomTat || tai.bieuDo || tai.dieuHanh || tai.bang)
  const coDuLieu = computed(() => tomTat.value !== null)

  /**
   * Số đang hiển thị thuộc bộ lọc KHÁC với bộ lọc vừa yêu cầu.
   *
   * Đây là chỗ phân biệt hai tình huống nhìn giống nhau mà phải xử lý ngược nhau:
   *  - Quay lại trang, bộ lọc y nguyên -> số cũ vẫn đúng, cứ hiện, tải ngầm bên dưới, KHÔNG làm
   *    mờ (làm mờ ở đây chỉ khiến màn hình chớp một cái vô cớ).
   *  - Vừa bấm Áp dụng -> số trên màn là của bộ lọc CŨ, phải làm mờ để người dùng biết nó sắp đổi.
   */
  const lechLoc = computed(() => khoaDuLieu.value !== khoaYeuCau.value)

  /**
   * Chống race, mỗi khối một bộ đếm riêng: đổi bộ lọc hai lần liên tiếp thì lượt đầu có thể về
   * SAU lượt hai và ghi đè, màn hình hiện số của bộ lọc cũ mà không ai tái hiện được.
   *
   * Không dùng được `useLatestRequest` ở đây vì composable đó gắn `onBeforeUnmount` — store
   * không có vòng đời component để gắn vào.
   */
  const luot = { tomTat: 0, bieuDo: 0, dieuHanh: 0, bang: 0 }

  function khoaTu(boLoc) {
    return [boLoc.from, boLoc.to, boLoc.schoolId ?? '', boLoc.categoryId ?? ''].join('|')
  }

  /** Gọi một khối; chỉ kết quả của lượt MỚI NHẤT được ghi vào store. */
  async function goi(ten, request, dat) {
    const cua = ++luot[ten]
    tai[ten] = true
    try {
      const { data } = await request()
      if (cua === luot[ten]) dat(data)
    } catch (e) {
      if (cua === luot[ten]) {
        loi.value = e?.response?.data?.message || 'Không tải được số liệu.'
      }
    } finally {
      if (cua === luot[ten]) tai[ten] = false
    }
  }

  /** Danh mục cho ô lọc — gần như không đổi, nạp một lần cho cả phiên. */
  async function napDanhMuc() {
    if (danhMuc.value.truong.length || danhMuc.value.nhomMon.length) return
    try {
      danhMuc.value = (await dashboardApi.filters()).data
    } catch {
      // Không có danh mục thì chỉ mất mấy ô lọc, phần còn lại vẫn chạy
    }
  }

  /** Bảng chi tiết của một chiều; đã có trong bộ đệm thì thôi, trừ khi ép tải lại. */
  function napBang(boLoc, chieu, epTaiLai = false) {
    if (!epTaiLai && bang.value[chieu]) return Promise.resolve()
    return goi(
      'bang',
      () => dashboardApi.breakdown(boLoc, chieu),
      (d) => {
        bang.value = { ...bang.value, [chieu]: d }
      },
    )
  }

  /** Đổi tab bảng chi tiết. Chiều chưa có trong bộ đệm thì mới gọi API. */
  function doiTab(boLoc, chieu) {
    tab.value = chieu
    return napBang(boLoc, chieu)
  }

  /**
   * Nạp toàn bộ màn hình.
   *
   * Bốn khối gọi ĐỘC LẬP, khối nào về trước hiện trước — `/summary` quét một lượt nên về gần như
   * tức thì, không việc gì phải nằm chờ những khối nặng hơn.
   */
  function nap(boLoc) {
    const k = khoaTu(boLoc)
    if (k !== khoaYeuCau.value) {
      khoaYeuCau.value = k
      bang.value = {} // số của bộ lọc cũ, không dùng lại được
    }
    loi.value = ''

    return Promise.all([
      goi(
        'tomTat',
        () => dashboardApi.summary(boLoc),
        (d) => {
          tomTat.value = d
          khoaDuLieu.value = k // từ đây số trên màn đã thuộc đúng bộ lọc vừa yêu cầu
        },
      ),
      goi(
        'bieuDo',
        () => dashboardApi.analytics(boLoc),
        (d) => (bieuDo.value = d),
      ),
      goi(
        'dieuHanh',
        () => dashboardApi.operations(boLoc),
        (d) => (dieuHanh.value = d),
      ),
      napBang(boLoc, tab.value, true),
    ])
  }

  return {
    khoaDuLieu,
    khoaYeuCau,
    tomTat,
    bieuDo,
    dieuHanh,
    bang,
    danhMuc,
    tab,
    viTriCuon,
    tai,
    loi,
    dangTai,
    coDuLieu,
    lechLoc,
    khoaTu,
    napDanhMuc,
    nap,
    doiTab,
  }
})
