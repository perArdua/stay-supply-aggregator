package io.github.perardua.staysupply.adapter;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierCodeChunksTest {

    private static List<String> codes(int size) {
        return IntStream.range(0, size).mapToObj(i -> "A-" + i).toList();
    }

    private static List<Integer> chunkSizes(int codeCount) {
        return SupplierCodeChunks.chunk(codes(codeCount)).stream().map(List::size).toList();
    }

    @Test
    void 숙소_코드가_없으면_빈_청크조차_만들지_않는다() {
        assertThat(SupplierCodeChunks.chunk(codes(0))).isEmpty();
    }

    @Test
    void 숙소_코드가_1개면_청크도_1개다() {
        assertThat(chunkSizes(1)).containsExactly(1);
    }

    @Test
    void 숙소_코드가_49개면_청크는_1개다() {
        assertThat(chunkSizes(49)).containsExactly(49);
    }

    @Test
    void 숙소_코드가_50개면_경계에서_쪼개지지_않고_청크는_1개다() {
        assertThat(chunkSizes(50)).containsExactly(50);
    }

    @Test
    void 숙소_코드가_51개면_청크는_2개다() {
        assertThat(SupplierCodeChunks.chunk(codes(51))).hasSize(2);
    }

    @Test
    void 숙소_코드가_51개면_청크_크기는_50과_1이다() {
        assertThat(chunkSizes(51)).containsExactly(50, 1);
    }

    @Test
    void 숙소_코드가_100개면_나머지가_없어도_빈_청크가_붙지_않는다() {
        assertThat(chunkSizes(100)).containsExactly(50, 50);
    }

    @Test
    void 숙소_코드가_120개면_청크_크기는_50과_50과_20이다() {
        assertThat(chunkSizes(120)).containsExactly(50, 50, 20);
    }

    @Test
    void 청크를_전부_합치면_원래_숙소_코드_목록과_같다() {
        List<String> original = codes(120);

        assertThat(SupplierCodeChunks.chunk(original).stream().flatMap(List::stream).toList())
                .isEqualTo(original);
    }
}
