#!/usr/bin/env python3
"""선착순 결과가 공정하고 정합한지 DB 에서 직접 확인한다.

k6 가 보는 것은 응답 코드뿐이라 "누가 이겼는가"를 알 수 없다. 승자 목록을 꺼내
offset 과 대조해야 도착 순서대로 팔렸는지 판정할 수 있다.

판정 넷:

  공정성   승자 offset 이 파티션의 첫 N 개와 정확히 일치하는가. 구멍이 있으면
           앞선 요청이 밀리고 뒤 요청이 이긴 것이다.
  오버셀   확보된 유닛이 재고를 넘지 않는가.
  언더셀   재고가 남았는데 매진 처리되지 않았는가.
  중복     한 offset 이 두 번 기록되지 않았는가.
"""
import json, subprocess, sys

VARIANT = int(sys.argv[1]) if len(sys.argv) > 1 else 1
STOCK = int(sys.argv[2]) if len(sys.argv) > 2 else 1000
OUT = sys.argv[3] if len(sys.argv) > 3 else None
PASS_ = "PASS"
FAIL_ = "FAIL"


def sql(pod, db, query):
    out = subprocess.run(
        ["kubectl", "-n", "ecommerce", "exec", "-i", pod, "--",
         "mysql", "-u", "root", "-pchangeme", "-N", "-B", "-e", query],
        capture_output=True, text=True)
    rows = [l.split("\t") for l in out.stdout.strip().splitlines()
            if l and "insecure" not in l.lower()]
    return rows


results = []


def check(name, ok, detail):
    results.append({"check": name, "verdict": PASS_ if ok else FAIL_, "detail": detail})
    print("  %-10s %-9s %s" % (PASS_ if ok else FAIL_, name, detail))


# 1. 승자 목록
winners = sql("mysql-order-0", "ecommerce_order",
              "SELECT partition_no, record_offset FROM ecommerce_order.flash_reservation "
              "WHERE variant_id=%d ORDER BY record_offset;" % VARIANT)
offsets = sorted(int(r[1]) for r in winners)
parts = {int(r[0]) for r in winners}

check("승자 수", len(offsets) == STOCK,
      "%d명 (재고 %d)" % (len(offsets), STOCK))

check("단일 파티션", len(parts) <= 1,
      "파티션 %s" % (sorted(parts) or "없음"))

# 2. 공정성: 승자 offset 이 연속인가
if offsets:
    span = offsets[-1] - offsets[0] + 1
    gaps = span - len(offsets)
    check("공정성", gaps == 0,
          "offset %d~%d, 구멍 %d개 (구멍이 있으면 앞선 요청이 밀린 것)"
          % (offsets[0], offsets[-1], gaps))
else:
    check("공정성", False, "승자가 없다")

# 3. 중복
check("중복 없음", len(offsets) == len(set(offsets)),
      "고유 offset %d / 전체 %d" % (len(set(offsets)), len(offsets)))

# 4. 오버셀 / 언더셀
units = sql("mysql-product-0", "ecommerce_product",
            "SELECT status, COUNT(*) FROM ecommerce_product.stock_unit "
            "WHERE variant_id=%d GROUP BY status;" % VARIANT)
by_status = {r[0]: int(r[1]) for r in units}
reserved = by_status.get("RESERVED", 0)
available = by_status.get("AVAILABLE", 0)

check("오버셀 없음", reserved <= STOCK,
      "확보 %d / 재고 %d" % (reserved, STOCK))
check("언더셀 없음", available == 0,
      "남은 재고 %d" % available)
check("승자=확보", len(offsets) == reserved,
      "승자 %d, 확보된 유닛 %d" % (len(offsets), reserved))

failed = [r for r in results if r["verdict"] == FAIL_]
summary = {"variantId": VARIANT, "stock": STOCK, "winners": len(offsets),
           "reserved": reserved, "available": available,
           "offset_min": offsets[0] if offsets else None,
           "offset_max": offsets[-1] if offsets else None,
           "checks": results, "verdict": FAIL_ if failed else PASS_}
if OUT:
    with open(OUT, "w") as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=2)

print("\n%s: %d개 검사 중 %d개 실패" % (summary["verdict"], len(results), len(failed)))
sys.exit(1 if failed else 0)
