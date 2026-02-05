package cn.scut.raputa.task;

import cn.scut.raputa.entity.TempFile;
import cn.scut.raputa.repository.TempFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TempFileCleaner {

    private final TempFileRepository repo;

    @Scheduled(cron = "0 0/30 * * * ?")
    public void clean() {
        List<TempFile> all = repo.findAll();
        LocalDateTime now = LocalDateTime.now(TempFile.ZONE_CN);
        for (TempFile t : all) {
            if (t.getExpireAt() != null && t.getExpireAt().isBefore(now)) {
                try {
                    Path path = Paths.get(t.getLocation());
                    Path dir = path.getParent();
                    Files.deleteIfExists(path);
                    if (dir != null && Files.isDirectory(dir)) {
                        try (var ds = Files.newDirectoryStream(dir)) {
                            if (!ds.iterator().hasNext())
                                Files.deleteIfExists(dir);
                        }
                    }
                } catch (Exception ignored) {
                }
                repo.deleteById(t.getId());
            }
        }
    }
}
